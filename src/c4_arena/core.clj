(ns c4-arena.core
  (:gen-class)
  (:require
   [manifold
    [stream :as s]
    [deferred :as d]]
   [aleph
    [http :as http]
    [tcp :as tcp]]
   [taoensso.timbre :as tb]))

;;; TCP connection for telnet interface
(defn text-handler [s info]
  (let [state (atom nil)]
    (tb/info info)
    (s/connect s s)))

;;; Websocket connection for the game protocol
(defn game-handler [req]
  (let [s @(http/websocket-connection req)]
    (s/connect s s))))

(defn -main [& args]
  (http/start-server handler {:port 8001})
  (tcp/start-server #'text-handler {:port 8002}))
