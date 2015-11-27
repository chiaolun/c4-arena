(ns c4-arena.core
  (:gen-class)
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alt! alts!]]]
   [clojure
    [string :as string]]
   [taoensso.timbre :as tb]
   [clojure.java.jdbc :as sql]
   [manifold
    [stream :as st]
    [deferred :as df]]
   [aleph
    [http :as http]]
   [compojure.core
    :refer [defroutes GET]]
   [clojure.tools.nrepl.server :as nrepl]
   [cider.nrepl :refer [cider-nrepl-handler]]
   [cheshire.core :refer [parse-string generate-string]]
   [c4-arena
    [c4-rules :refer [ncols nrows make-move get-winner]]
    [players :refer [spawn-random-player spawn-aima-player]]]))

(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "db/matches.sqlite"})

(defonce matcher (atom nil))
;;; All the ids waiting for a match
(defonce awaiting (atom nil))

(defonce uid-counter (atom 0))

(declare initial-loop)
(defn game-loop [players]
  (let [players (shuffle players)
        ch-ins (mapv :ch-in players)
        ch-outs (mapv :ch-out players)
        state (atom (vec (repeat (* ncols nrows) 0)))
        last-move (atom {})
        turn (atom 0)
        winner (atom nil)
        process-move (fn [move]
                       ;; This function enforces the rules of
                       ;; connect-4. If a move is not valid, returns
                       ;; false. If a move is valid, it makes the move
                       ;; and then determines if there has been a
                       ;; winner. If there has been a winner, it marks
                       ;; the winner. Flips turn and returns true at
                       ;; this point
                       (let [[new-state-val i] (make-move @state move (inc @turn))]
                         (when new-state-val
                           (reset! state new-state-val)
                           (swap! turn #(- 1 %))
                           (reset! winner (get-winner @state i))
                           (reset! last-move [move move])
                           true)))
        notify (fn [ch]
                 (put! ch (cond->
                              {:type "state"
                               :turn (inc (or @turn -1))
                               :you (inc (.indexOf ch-outs ch))
                               :state @state}
                            (@last-move (.indexOf ch-outs ch))
                            (assoc :last-move (@last-move (.indexOf ch-outs ch)))
                            @winner
                            (assoc
                             :winner (inc @winner)
                             :turn 0)))
                 (swap! last-move assoc (.indexOf ch-outs ch) nil))]
    (go
      ;; Start out by notifying both sides of the board state
      (doseq [ch-out ch-outs]
        (notify ch-out))
      ;; During the game
      (loop []
        (when-let [[{:keys [type] :as msg} ch-in] (alts! ch-ins)]
          (let [actor (.indexOf ch-ins ch-in)
                ch-out (ch-outs actor)]
            (if-not msg
              ;; Cleanup because someone disconnected
              (doseq [{ch-out0 :ch-out :as player0} players
                      :when (not= ch-out ch-out0)]
                (notify ch-out0)
                (put! ch-out0 {:type "disconnected"})
                (initial-loop player0))
              (let [reason (cond
                             (not (#{"state_request" "move"} type))
                             "Unknown message type"
                             (= type "state_request") nil
                             (not= @turn actor)
                             "Not your turn"
                             ;; Move gets processed here via
                             ;; side-effect (boo!)
                             (not (process-move (:move msg)))
                             "Invalid move")]
                (cond
                  reason
                  (put! ch-out {:type "ignored" :msg msg :reason reason})
                  (= type "state_request")
                  (notify ch-out)
                  :else
                  (doseq [ch-out ch-outs]
                    (notify ch-out)))
                (if-not @winner
                  (recur)
                  ;; Cleanup because someone won
                  (doseq [{ch-out0 :ch-out :as player0} players]
                    (put! ch-out0 {:type "end"})
                    (initial-loop player0)))))))))))

(defn await-loop [{:keys [waiter] :as player}]
  (go-loop []
    (alt!
      waiter
      ([_]
       ;; If the control channel is closed, dequeue and stop
       ;; processing messages (note the lack of recur)
       (swap! awaiting dissoc (:uid player)))
      (:ch-in player)
      ([msg]
       (if-not msg
         ;; If the client closes their connection, dequeue and stop
         ;; loop
         (swap! awaiting dissoc (:uid player))
         ;; Otherwise ignore all messages of the client
         (do (put! (:ch-out player)
                   {:type "ignored" :msg msg :reason "Waiting for match"})
             (recur)))))))

(defonce match-count (atom {}))
(defonce active-matches (atom #{}))
(defn match-once [{:keys [id against] :as player0}]
  (if-let [player1 (or
                    (cond
                      (= against "random")
                      (spawn-random-player)
                      (= against "aima")
                      (spawn-aima-player))
                    (->> (vals @awaiting)
                         (remove
                          (fn [{other-id :id}]
                            (= other-id id)))
                         ;; If present, play against requested player
                         (filter
                          (fn [{other-id :id}]
                            (or (nil? against)
                                (= other-id against))))
                         ;; Enforce in the other direction as well
                         (filter
                          (fn [{other-against :against}]
                            (or (nil? other-against)
                                (= id other-against))))
                         ;; Do not match if there is an active game
                         ;; going on
                         (remove
                          (fn [{other-id :id}]
                            (@active-matches #{id other-id})))
                         ;; Try to match with the least matched person
                         (sort-by
                          (fn [{other-id :id}]
                            (@match-count #{id other-id})))
                         first))]
    ;; Someone is available!
    (do (async/close! (:waiter player1))
        (let [pair #{(:id player0) (:id player1)}]
          (swap! match-count update-in [pair] (fnil inc 0))
          (swap! active-matches conj pair)
          (go
            (<! (game-loop (->> [player0 player1] (map #(dissoc % :waiter)))))
            ;; Loop ends when the game is over. We can then mark the
            ;; match as no longer active
            (swap! active-matches disj pair))))
    ;; Nobody is available
    (let [player0 (assoc player0 :waiter (chan))]
      (swap! awaiting assoc (:uid player0) player0)
      (await-loop player0))))

(defn matcher-init []
  ;; This function initializes the matcher channel, which is needed
  ;; because the data structures used by match-once are not
  ;; thread-safe - it acts as a lock and serializes access to the
  ;; awaiting queue
  (let [ch (chan)]
    (go-loop []
      (when-let [player (<! ch)]
        (try
          (match-once player)
          (catch Exception e
            (tb/error e)))
        (recur)))
    (when-let [prev-ch @matcher]
      (async/close! prev-ch))
    (reset! matcher ch)))

(defn initial-loop [{:keys [ch-in ch-out] :as player}]
  (go-loop []
    (when-let [msg (<! ch-in)]
      (let [{:keys [type id against]} msg
            reason (cond
                     (not= type "start")
                     "Only start messages allowed in current state"
                     (string/blank? id)
                     "You need to include an id"
                     (#{"random" "aima"} id)
                     (format "\"%s\" is a reserved id used for a reference player" id))]
        (if-not reason
          (>! @matcher (assoc player :id id :against against))
          (do (put! ch-out {:type "ignored" :msg msg :reason reason})
              (recur)))))))

;;; Game loop
(defn game-init [s]
  (let [ch-in (chan)
        ch-out (chan)
        uid (swap! uid-counter inc)
        player {:uid uid :ch-in ch-in :ch-out ch-out}]
    ;; Incoming messages
    (st/connect (st/map #(tb/spy :debug (parse-string % true)) s) ch-in)
    ;; Outgoing messages
    (st/connect (st/map #(generate-string (tb/spy :debug %)) ch-out) s)
    ;; Put into initial loop
    (initial-loop player)
    {:status 200 :body "success!"}))

;;; Websocket connection for the game protocol
(defn game-websocket [req]
  (let [s @(http/websocket-connection req)]
    (game-init s)))

;; ;;; Websocket connection for the firehose, carrying all state updates
;; ;;; for the whole server
;; (defn firehose-handler [req]
;;   (let [s @(http/websocket-connection req)]
;;     ;; TODO
;;     ))

(defroutes app
  ;; (GET "/firehose" [] #'firehose-handler)
  (GET "/" [] #'game-websocket))

(defn -main [& args]
  (nrepl/start-server
   :port 4222
   :handler cider-nrepl-handler)
  (matcher-init)
  (http/start-server #'app {:port 8001})
  (tb/info "Server is up!"))
