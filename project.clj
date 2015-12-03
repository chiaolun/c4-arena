(defproject c4-arena "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [cider/cider-nrepl "0.9.1"]
                 [org.clojure/core.async "0.2.371"]
                 [com.taoensso/timbre "4.1.4"]
                 [compojure "1.4.0"]
                 [aleph "0.4.0" :exclusions [io.netty/netty-all]]
                 ;; This is to get around a bug in netty 4.1.0.Beta4
                 ;; with binding EC2 interfaces
                 [io.netty/netty-all "4.1.0.Beta7"]
                 [cheshire "5.5.0"]
                 [com.googlecode.aima-java/aima-core "0.10.5"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [migratus "0.8.7"]]
  :plugins [[cider/cider-nrepl "0.9.1"]
            [migratus-lein "0.2.0"]]
  :main ^:skip-aot c4-arena.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :migratus {:store :database
             :migration-dir "migrations"
             :db {:classname "org.sqlite.JDBC"
                  :subprotocol "sqlite"
                  :subname "db/matches.sqlite"}})
