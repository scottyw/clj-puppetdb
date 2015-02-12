(ns clj-puppetdb.testutils.repl
  (:require [clojure.tools.namespace.repl :refer (refresh)]))

(defn reset []
  (refresh))
