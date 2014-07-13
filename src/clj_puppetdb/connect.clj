(ns clj-puppetdb.connect
  (:require [clojure.java.io :as io]
            [puppetlabs.http.client.sync :as http]))

(defprotocol PDBConnection
  (GET [this path]))

(deftype HTTPConnection
    [url]
  PDBConnection
  (GET [_ path] (println (format "Getting %s/%s" url path))))

(deftype HTTPSConnection
    [url opts]
  PDBConnection
  (GET [_ path opts] (http/get (str url path) opts)))

(defn )
