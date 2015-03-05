(ns clj-puppetdb.core-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]
            [clojure.java.io :as io]))

(deftest connect-test
  (testing "Should create a connection with http://localhost:8080 with no additional arguments"
    (let [conn (connect "http://localhost:8080")]
      (is (= conn {:host "http://localhost:8080" :opts {:vcr-dir nil}}))))
  (testing "Should create a connection with http://localhost:8080 and VCR enabled"
    (let [conn (connect "http://localhost:8080" {:vcr-dir "/temp"})]
      (is (= conn {:host "http://localhost:8080" :opts {:vcr-dir "/temp"}}))))
  (testing "Should accept https://puppetdb:8081 with a map of (dummy) certs"
    (let [opts {:ssl-ca-cert (io/file "src/clj_puppetdb/core.clj")
                :ssl-cert    (io/file "src/clj_puppetdb/core.clj")
                :ssl-key     (io/file "src/clj_puppetdb/core.clj")
                :vcr-dir     nil}
          conn (connect "https://puppetdb:8081" opts)]
      ;; I'm only testing for truthiness of conn here. Schema validation should handle the rest,
      ;; and testing equality with java.io.File objects doesn't seem to work.
      (is conn)))
  (testing "Should accept https://puppetdb:8081 with a map of (dummy) certs and VCR enabled"
    (let [opts {:ssl-ca-cert (io/file "src/clj_puppetdb/core.clj")
                :ssl-cert    (io/file "src/clj_puppetdb/core.clj")
                :ssl-key     (io/file "src/clj_puppetdb/core.clj")
                :vcr-dir     "/temp"}
          conn (connect "https://puppetdb:8081" opts)]
      ;; I'm testing for truthiness of conn here. Schema validation should handle the rest except the VCR piece,
      ;; and testing equality with java.io.File objects doesn't seem to work.
      (is conn)
      (is (= "/temp" (get-in conn [:opts :vcr-dir]))))))
