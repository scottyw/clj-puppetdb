(defproject clj-puppetdb "0.1.2-SNAPSHOT"
            :description "A Clojure client for the PuppetDB REST API"
            :url "https://github.com/puppetlabs/clj-puppetdb"
            :license {:name "Apache License, Version 2.0"
                      :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [puppetlabs/http-client "0.3.0"]
                           [prismatic/schema "0.2.4"]
                           [com.cemerick/url "0.1.1"]
                           [cheshire "5.3.1"]
                           [pandect "0.5.1"]]

            :repl-options {:init (do (require 'spyscope.core)
                                     (use 'clj-puppetdb.testutils.repl))}

            :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                                            [spyscope "0.1.5"]]}})
