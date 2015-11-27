(ns c4-arena.players
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alt! alts!]]]
   [taoensso.timbre :as tb]
   [c4-arena
    [c4-rules :refer [nrows ncols]]])
  (:import
   [aima.core.environment.connectfour
    ConnectFourState ConnectFourGame ConnectFourAIPlayer]))

(defn spawn-random-player []
  (let [ch-in (chan) ch-out (chan)]
    (go-loop []
      (when-let [msg (<! ch-out)]
        (condp contains? (:type msg)
          #{"end" "disconnected"} :terminate
          #{"ignored"} (do
                         (put! ch-in {:type "state_request"})
                         (recur))
          #{"state"} (let [{:keys [state turn you]} msg]
                       (when (= turn you)
                         (put! ch-in {:type "move" :move (rand-int ncols)}))
                       (recur)))))
    {:id "random" :waiter (chan) :ch-in ch-in :ch-out ch-out}))

(defn spawn-aima-player []
  (let [ch-in (chan) ch-out (chan)
        c4-state (ConnectFourState. nrows ncols)
        search (ConnectFourAIPlayer. (ConnectFourGame.) 0.5)]
    (go-loop []
      (when-let [msg (<! ch-out)]
        (condp contains? (:type msg)
          #{"end" "disconnected"} :terminate
          #{"ignored"} (tb/error "Invalid move, terminating")
          #{"state"} (let [{:keys [state turn you last-move]} msg]
                       (when last-move
                         (.dropDisk c4-state last-move))
                       (when (= turn you)
                         (put! ch-in {:type "move" :move (.makeDecision search c4-state)}))
                       (recur)))))
    {:id "random" :waiter (chan) :ch-in ch-in :ch-out ch-out}))
