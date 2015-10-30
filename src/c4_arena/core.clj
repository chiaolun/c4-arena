(ns c4-arena.core
  (:gen-class)
  (:require
   [clojure.core
    [async :refer [go go-loop <! >! chan]]]
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
(defonce awaiting-match (atom nil))

(defn matcher-init []
  (let [ch (chan)]
    (go-loop []
      (when-let [{:keys [id chs latch]} (<! ch)]

        (recur)))
    (reset! matcher ch)))

;;; Game handler
(defn game-handler [[ch-in ch-out :as chs]]
  (go-loop []
    (when-let [msg (<! ch-in)]
      (let [{:keys [type id]} msg]
        (cond
          (and (= type "start") id)
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

;;; Websocket connection for the firehose, carrying all state updates
;;; for the whole server
(defn firehose-handler [req]
  (let [s @(http/websocket-connection req)]
    ;; TODO
    ))

(defroutes app
  (GET "/firehose" [] #'firehose-handler)
  (GET "/" [] #'game-handler))

(defn -main [& args]
  (matcher-init)
  (http/start-server #'game-handler {:port 8001}))
