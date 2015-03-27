(ns clj-puppetdb.core-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]))

(deftest connect-test
  (testing "Should create a connection with http://localhost:8080 with no additional arguments"
    (let [conn (connect "http://localhost:8080")]
      (is (= conn {:host "http://localhost:8080" :opts {:vcr-dir nil}}))))
  (testing "Should create a connection with http://localhost:8080 and VCR enabled"
    (let [conn (connect "http://localhost:8080" {:vcr-dir "/temp"})]
      (is (= conn {:host "http://localhost:8080" :opts {:vcr-dir "/temp"}}))))
  (testing "Should accept https://puppetdb:8081 with a map of test certs"
    (let [opts {:ssl-ca-cert "./dev-resources/certs/ca-cert.pem"
                :ssl-cert    "./dev-resources/certs/cert.pem"
                :ssl-key     "./dev-resources/certs/key.pem"
                :vcr-dir     nil}
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
      (is (= "/temp" (get-in conn [:opts :vcr-dir]))))))
