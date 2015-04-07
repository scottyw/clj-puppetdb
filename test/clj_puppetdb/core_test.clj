(ns clj-puppetdb.core-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]
            [clj-puppetdb.http :refer [GET]]
            [puppetlabs.http.client.sync :as http]))

(deftest connect-test
  (testing "Should create a connection with http://localhost:8080 with no additional arguments"
    (let [conn (connect "http://localhost:8080")]
      (is (= (conn) {:host "http://localhost:8080"}))))
  (testing "Should create a connection with http://localhost:8080 and VCR enabled"
    (let [conn (connect "http://localhost:8080" {:vcr-dir "/temp"})]
      (is (= (conn) {:host "http://localhost:8080" :vcr-dir "/temp"}))))
  (testing "Should accept https://puppetdb:8081 with a map of test certs"
    (let [opts {:ssl-ca-cert "./dev-resources/certs/ca-cert.pem"
                :ssl-cert    "./dev-resources/certs/cert.pem"
                :ssl-key     "./dev-resources/certs/key.pem"}
          conn (connect "https://puppetdb:8081" opts)]
      ;; I'm only testing for truthiness of conn here. Schema validation should handle the rest,
      ;; and testing equality with java.io.File objects doesn't seem to work.
      (is conn)))
  (testing "Should accept https://puppetdb:8081 with a map of test certs and VCR enabled"
    (let [opts {:ssl-ca-cert "./dev-resources/certs/ca-cert.pem"
                :ssl-cert    "./dev-resources/certs/cert.pem"
                :ssl-key     "./dev-resources/certs/key.pem"
                :vcr-dir     "/temp"}
          conn (connect "https://puppetdb:8081" opts)]
      ;; I'm testing for truthiness of conn here. Schema validation should handle the rest except the VCR piece,
      ;; and testing equality with java.io.File objects doesn't seem to work.
      (is conn)
      (is (= (:vcr-dir opts) (:vcr-dir (conn))))
      (is (:ssl-context (conn)))))
  (testing "SSL connection should require certificates"
    (is (thrown? IllegalArgumentException (connect "https://puppetdb:8081" {})))
    (is (thrown? IllegalArgumentException (connect "https://puppetdb:8081"
                                                   {:ssl-context "dummy"})))
    (is (thrown? IllegalArgumentException (connect "https://puppetdb:8081"
                                                   {:ssl-cert "dummy" :ssl-key "dummy" :ssl-ca-cert "dummy"}))))
  (testing "Should do proper GET request"
    (let [host "http://localhost:8080"
          path "/v4/nodes"
          params {:query [:= [:fact "operatingsystem"] "Linux"]}
          url-params "?query=%5B%22%3D%22%2C%5B%22fact%22%2C%22operatingsystem%22%5D%2C%22Linux%22%5D"
          conn (connect host)]
      (with-redefs [http/get (fn[query _] (is (= query (str host path url-params))) [])]
        (conn conn path params)
        (conn (str host path url-params))))))

(deftest query-test
  (let [data ["node-01" "node-02" "node-03" "node-04"]
        metadata {:total (count data)}
        client "a client"
        path "/v4/nodes"]
    (testing "Should return data when parameters are not used"
      (with-redefs [GET (fn[_ _ _]
                          [data {"x-records" (.toString (count data))}])]
        (is (= (query client path)
               data))
        (is (= (query-with-metadata client path {})
               [data metadata]))))
    (testing "Should return data when parameters are used"
      (let [query-vec [:= [:fact "operatingsystem"] "Linux"]]
        (with-redefs [GET (fn[_ _ params]
                            (is (= (:query params) query-vec))
                            [data {"x-records" (.toString (count data))}])]
          (is (= (query client path query-vec)
                 data))
          (is (= (query-with-metadata client path query-vec {})
                 [data metadata]))
          (is (= (query-with-metadata client path {:query query-vec})
                 [data metadata])))))))

(deftest lazy-query-test
  (testing "Should automatically fetch three pages by two items"
    (let [data [["A" "B"] ["C" "D"] ["E" "F"]]]
      (with-redefs [clj-puppetdb.http/GET (fn[_ _ {:keys [offset]}]
                                            (let [idx (/ offset 2)]
                                              (when (< idx 3) (nth data idx))))]
        (let [seq (lazy-query nil nil {:limit 2 :order-by nil})]
          (is (= seq (flatten data)))))))
  (testing "Should automatically fetch two pages by tree items"
    (let [data [["A" "B" "C"] [ "D" "E" "F"]]]
      (with-redefs [clj-puppetdb.http/GET (fn[_ _ {:keys [offset]}]
                                            (let [idx (/ offset 3)]
                                              (when (< idx 2) (nth data idx))))]
        (let [seq (lazy-query nil nil {:limit 3 :order-by nil})]
          (is (= seq (flatten data))))))))