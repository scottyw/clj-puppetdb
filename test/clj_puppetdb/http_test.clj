(ns clj-puppetdb.http-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clj-puppetdb.http :refer [GET make-client]]
            [puppetlabs.http.client.sync :as http]
            [cheshire.core :as json])
  (:import (clojure.lang ExceptionInfo)))

(deftest GET-test
  (let [host "http://localhost:8080"
        path "/v4/nodes"
        params {:query [:= [:fact "operatingsystem"] "Linux"]}
        client (make-client host {})
        response-data ["node-1" "node-2"]
        response-data-encoded (json/encode response-data)
        response-headers {"x-records" (.toString (count response-data))}
        fake-get (fn[status] {:status status :body (io/input-stream (.getBytes response-data-encoded)) :headers response-headers})]

      (testing "Should have proper response"
        (with-redefs [http/get (fn[_ _] (fake-get 200))]
          (let [GET-response (GET client path params)]
            (is (= (first GET-response) response-data))
            (is (= (second GET-response) response-headers)))))

      (testing "Should throw proper exception"
        (with-redefs [http/get (fn[_ _] (fake-get 400))]
          (try
            (GET client path params)
            (catch ExceptionInfo ei
              (let [info (.getData ei)]
                (is (= (:status info) 400))
                (is (= (:kind info) :puppetdb-query-error))
                (is (= (:params info) params))
                (is (= (:endpoint info) path))
                (is (= (:host info) host))
                (is (= (:msg info) response-data-encoded)))))))))
