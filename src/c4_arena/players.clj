(ns c4-arena.players
  (:require
   [clojure.core
    [async :as async :refer [go go-loop <! >! chan put! alt! alts!]]]
   [taoensso.timbre :as tb]
   [clojure [string :as string]]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [c4-arena
    [c4-rules :refer [nrows ncols]]])
  (:import
   [aima.core.environment.connectfour
    ConnectFourState ConnectFourGame ConnectFourAIPlayer]
   [aima.core.search.adversarial
    AlphaBetaSearch]))

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
    {:id "random" :ch-in ch-in :ch-out ch-out}))

(defn spawn-aima-player [time-to-think]
  (let [ch-in (chan) ch-out (chan)
        c4-state (ConnectFourState. nrows ncols)
        search (ConnectFourAIPlayer. (ConnectFourGame.) time-to-think)]
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
    {:id "aima" :ch-in ch-in :ch-out ch-out}))

(defn tromp-str [^ConnectFourState state]
  (->> (for [col (range (.getCols state))
             row (reverse (range (.getRows state)))]
         (case (.getPlayerNum state row col)
           0 "b" 1 "x" 2 "o"))
       doall (apply str)))

(defonce tromp-db
  (with-open [in-file (io/reader (io/resource "tromp.csv"))]
    (->> (csv/read-csv in-file)
         (map (fn [row]
                [(apply str (subvec row 0 (dec (count row))))
                 (case (last row)
                   "win" 1.0 "loss" 0.0 "draw" 0.5)]))
         (into {}))))

(defn tromp-terminated-connect4 []
  (proxy [ConnectFourState] [6 7]
    (getUtility []
      (or
       (-> this tromp-str tromp-db)
       (proxy-super getUtility)))))

(defn spawn-perfect-player []
  (let [ch-in (chan) ch-out (chan)
        c4-state (tromp-terminated-connect4)
        search (ConnectFourAIPlayer. (ConnectFourGame.) 1.)]
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
    {:id "aima" :ch-in ch-in :ch-out ch-out}))
