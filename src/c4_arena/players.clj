(ns c4-arena.players
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alt! alts!]]]
   [taoensso.timbre :as tb]))

(defn spawn-random-player [ncols]
  (let [ch-in (chan) ch-out (chan)]
    (go-loop []
      (when-let [msg (<! ch-out)]
        (condp contains? (:type msg)
          #{"end" "disconnected"} :terminate
          #{"ignored"} (recur)
          #{"state"} (let [{:keys [state turn you]} msg]
                       (when (= turn you)
                         (put! ch-in {:type "move" :move (rand-int ncols)}))
                       (recur)))))
    {:id "random" :waiter (chan) :ch-in ch-in :ch-out ch-out}))
