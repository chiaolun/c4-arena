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
    AdversarialSearch AlphaBetaSearch MinimaxSearch]))

(set! *warn-on-reflection* true)

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
                         (.dropDisk #^ConnectFourState c4-state last-move))
                       (when (= turn you)
                         (put! ch-in {:type "move" :move (.makeDecision #^AdversarialSearch search c4-state)}))
                       (recur)))))
    {:id "aima" :ch-in ch-in :ch-out ch-out}))

(defn tromp-strs [^ConnectFourState state]
  (let [crange (range 7)]
    (for [cols [crange (reverse crange)]]
      (->> (for [col cols
                 row (reverse (range (.getRows state)))]
             (case (.getPlayerNum state row col)
               0 "b" 1 "x" 2 "o"))
           doall (apply str)))))

(defonce tromp-db
  (->>["tromp.csv" "tromp_extras.csv"]
      (mapcat
       (fn [file0]
         (with-open [in-file (io/reader (io/resource file0))]
           (->> (csv/read-csv in-file)
                (map
                 (fn [row]
                   [(apply str (subvec row 0 (dec (count row))))
                    (case (last row)
                      "win" 1.0 "loss" 0.0 "draw" 0.5)]))
                doall))))
      (into {})))

(comment
  ;; This prints out all the states not in the database
  (.makeDecision
   (AlphaBetaSearch. (ConnectFourGame.))
   (let [seen (atom #{})]
     (proxy [ConnectFourState] [6 7]
       (getUtility []
         (or
          (when (= (.getMoves this) 8)
            (let [boards (vec (tromp-strs this))]
              (if-let [score (some tromp-db boards)]
                score
                (when (< (proxy-super getUtility) -1e-5)
                  (when-not (some @seen boards)
                    (println (first boards))
                    (spit "unknowns" (str (first boards) "\n") :append true)
                    (swap! seen conj (first boards)))
                  0.5))))
          (proxy-super getUtility)))))))

(defn spawn-perfect-player []
  (let [ch-in (chan) ch-out (chan)
        c4-state (proxy [ConnectFourState] [6 7]
                   (getUtility []
                     (let [^ConnectFourState this this]
                       (or
                        (when (= (.getMoves this) 8)
                          (some->> this tromp-strs (some tromp-db)))
                        (proxy-super getUtility)))))
        ;; search (ConnectFourAIPlayer. (ConnectFourGame.) 1.)
        search (AlphaBetaSearch. (ConnectFourGame.))]
    (go-loop []
      (when-let [msg (<! ch-out)]
        (condp contains? (:type msg)
          #{"end" "disconnected"} :terminate
          #{"ignored"} (tb/error "Invalid move, terminating")
          #{"state"} (let [{:keys [state turn you last-move]} msg]
                       (when last-move
                         (.dropDisk #^ConnectFourState c4-state last-move))
                       (when (= turn you)
                         (put! ch-in {:type "move" :move (.makeDecision #^AdversarialSearch search c4-state)}))
                       (recur)))))
    {:id "aima" :ch-in ch-in :ch-out ch-out}))
