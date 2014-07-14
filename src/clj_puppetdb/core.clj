(ns clj-puppetdb.core
  (:require [clj-puppetdb.endpoints.facts :as facts]
            [clojure.java.io :as io]
            [puppetlabs.http.client.sync :as http]
            [cheshire.core :as json]))


