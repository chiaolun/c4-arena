(ns c4-arena.core
  (:gen-class)
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alts!]]]
   [taoensso.timbre :as tb]
   [manifold
    [stream :as st]
    [deferred :as d]]
   [aleph
    [http :as http]]
   [compojure.core
    :refer [defroutes GET]]
   [cheshire.core :refer [parse-string generate-string]]))

(defonce matcher (atom nil))
;;; All the ids waiting for a match
(defonce awaiting (atom nil))

(defonce match-count (atom {}))

(defn process-move [state winner turn move]
  true)

(defn game-loop [players]
  (let [ch-ins (vec (map (comp first :chs) players))
        ch-outs (vec (map (comp second :chs) players))
        state (atom (repeat (* 7 6) 0))
        turn (atom 0)
        winner (atom nil)
        notify (fn [ch]
                 (go (>! ch
                         (cond->
                             {:type :state
                              :turn (inc @turn)
                              :you (inc @turn)
                              :state @state}
                           @winner
                           (assoc
                            :winner @winner
                            :turn 0)))))]
    (go
      ;; During the game
      (loop []
        (<! (notify (ch-outs @turn)))
        (when-let [[{:keys [type move] :as msg} ch-in] (alts! ch-ins)]
          (when msg
            (let [actor (.indexOf ch-ins ch-in)
                  ch-out (ch-outs actor)]
              (cond
                (= type "state_request")
                (<! (notify ch-out))
                (and (= type "move")
                     (= @turn actor)
                     (process-move state winner turn move))
                :next-turn
                :else (>! ch-out {:type :ignored :msg msg})))
            (when-not @winner
              (recur)))))
      ;; Cleanup
      (doseq [{:keys [latch ch-out]} players]
        (<! (notify ch-out))
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
        (swap! match-count update-in [#{(:id player0) (:id player1)}] (fnil inc 0))
        (game-loop [player0 player1]))
    (swap! awaiting conj player0)))

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
(defn game-handler [[ch-in ch-out :as chs]]
  (go-loop []
    (when-let [msg (<! ch-in)]
      (let [{:keys [type id]} msg]
        (if (and (= type "start") id)
          (let [latch (chan)]
            (>! @matcher {:id id :chs chs :latch latch})
            ;; Waits here until game ends
            (<! latch))
          (do (>! ch-out {:type "ignored" :msg msg})
              (recur)))))))

;;; Game loop
(defn game-init [s]
  (let [ch-in (chan)
        ch-out (chan)
        chs [ch-in ch-out]]
    ;; Incoming messages
    (st/connect (st/map #(parse-string % true) s) ch-in)
    ;; Outgoing messages
    (st/connect (st/map generate-string ch-out) s)
    ;; Event loop for the connection
    (go
      ;; Keep waiting to start new games if channels are still alive
      (<! (game-handler chs))
      (st/close! s))))

;;; Websocket connection for the game protocol
(defn game-handler [req]
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
  (GET "/" [] #'game-handler))

(defn -main [& args]
  (matcher-init)
  (http/start-server #'game-handler {:port 8001}))
