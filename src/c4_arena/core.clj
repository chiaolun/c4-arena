(ns c4-arena.core
  (:gen-class)
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alt! alts!]]]
   [taoensso.timbre :as tb]
   [manifold
    [stream :as st]
    [deferred :as df]]
   [aleph
    [http :as http]]
   [compojure.core
    :refer [defroutes GET]]
   [cheshire.core :refer [parse-string generate-string]]))

(def ncols 7)
(def nrows 6)
(def n-to-win 4)

(defonce matcher (atom nil))
;;; All the ids waiting for a match
(defonce awaiting (atom nil))

(defonce match-count (atom {}))

(defn get-winner [state-val i]
  (let [cand (state-val i)
        dirs [1 nrows (inc nrows)]]
    (when (first
           (for [dir dirs]
             (<= (dec n-to-win)
                 (+
                  (count
                   (for [j (reductions + (repeat (- dir)))
                         :while (and (<= 0 j (dec ncols))
                                     (= cand (state-val j)))]
                     true))
                  (count
                   (for [j (reductions + (repeat (+ dir)))
                         :while (and (<= 0 j (dec ncols))
                                     (= cand (state-val j)))]
                     true))))))
      (dec cand))))

(defn process-move [state winner turn move]
  ;; This function implements the rules of connect-4. If a move is not
  ;; valid, returns false. If a move is valid, it makes the move and
  ;; then determines if there has been a winner. If there has been a
  ;; winner, it marks the winner. Flips turn and returns true at this point
  (when-let [i (and
                (<= 0 move (dec ncols))
                (->> (range (* move nrows) (* (inc move) nrows))
                     (filter (fn [i] (= (@state i) 0)))
                     first))]
    (swap! state assoc i (inc @turn))
    (swap! turn #(- 1 %))
    (reset! winner (get-winner @state i))
    true))

(defn game-loop [players]
  (let [ch-ins (mapv :ch-in players)
        ch-outs (mapv :ch-out players)
        state (atom (vec (repeat (* ncols nrows) 0)))
        turn (atom 0)
        winner (atom nil)
        notify (fn [ch]
                 (put! ch (cond->
                              {:type :state
                               :turn (inc (or @turn -1))
                               :you (inc (.indexOf ch-outs ch))
                               :state @state}
                            @winner
                            (assoc
                             :winner (inc @winner)
                             :turn 0))))]
    (go
      (doseq [ch-out ch-outs]
        (notify ch-out))
      ;; During the game
      (loop []
        (when-let [[{:keys [type move] :as msg} ch-in] (alts! ch-ins)]
          (when msg
            (let [actor (.indexOf ch-ins ch-in)
                  ch-out (ch-outs actor)]
              (cond
                (= type "state_request")
                (notify ch-out)
                (and (= type "move")
                     (= @turn actor)
                     (process-move state winner turn move))
                (notify (ch-outs @turn))
                :else (put! ch-out {:type :ignored :msg msg})))
            (when-not @winner
              (recur)))))
      ;; Cleanup
      (doseq [{:keys [latch ch-out]} players]
        (notify ch-out)
        (>! ch-out {:type (if @winner "end" "disconnected")})
        (async/close! latch)))))

(defn match-once [{:keys [id] :as player0}]
  (if-let [player1 (->> @awaiting
                        (remove
                         (fn [{other-id :id}]
                           (= id other-id)))
                        ;; Try to match with the least matched person
                        (sort-by
                         (fn [{other-id :id}]
                           (@match-count #{id other-id})))
                        first)]
    (do (swap! awaiting (partial remove #{player1}))
        (async/close! (:waiter player1))
        (swap! match-count update-in [#{(:id player0) (:id player1)}] (fnil inc 0))
        (game-loop [player0 player1]))
    (let [waiter (chan)]
      (go-loop []
        (alt!
          waiter :done
          (:ch-in player0)
          ([msg]
           (when msg
             (put! (:ch-out player0)
                   {:type :ignored :msg msg :reason "waiting for match"})
             (recur)))))
      (swap! awaiting conj (assoc player0 :waiter waiter)))))

(defn matcher-init []
  (let [ch (chan)]
    (go-loop []
      (when-let [player (<! ch)]
        (match-once player)
        (recur)))
    (when-let [prev-ch @matcher]
      (async/close! prev-ch))
    (reset! matcher ch)))

;;; Game handler
(defn game-handler [[ch-in ch-out]]
  (go-loop []
    (when-let [msg (<! ch-in)]
      (let [{:keys [type id]} msg]
        (if (and (= type "start") id)
          (let [latch (chan)]
            (>! @matcher {:id id :ch-in ch-in :ch-out ch-out :latch latch})
            ;; Waits here until game ends
            (<! latch))
          (>! ch-out {:type "ignored" :msg msg}))
        (recur)))))

;;; Game loop
(defn game-init [s]
  (let [ch-in (chan)
        ch-out (chan)]
    ;; Incoming messages
    (st/connect (st/map #(parse-string % true) s) ch-in)
    ;; Outgoing messages
    (st/connect (st/map generate-string ch-out) s)
    ;; Event loop for the connection
    (go
      ;; Keep waiting to start new games if channels are still alive
      (<! (game-handler [ch-in ch-out]))
      (st/close! s))
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
  (matcher-init)
  (http/start-server #'app {:port 8001}))
