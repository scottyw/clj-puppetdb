(ns clj-puppetdb.endpoints.facts)

(defn facts
  "GET the response from the /v3/facts endpoint as a seq of maps."
  [connection]
  (-> (str connection "/v3/facts")
       http/get
       :body
       (json/decode keyword)))
