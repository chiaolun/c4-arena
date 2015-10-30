(defproject c4-arena "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "4.1.4"]
                 [compojure "1.4.0"]
                 [aleph "0.4.0"]
                 [cheshire "5.5.0"]]
  :plugins [[cider/cider-nrepl "0.9.1"]]
  :main ^:skip-aot c4-arena.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
