(ns clj-puppetdb.vcr-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]
            [clj-puppetdb.http :refer :all]
            [puppetlabs.http.client.sync :as http]
            [me.raynes.fs :as fs]))

(def mock-http-response {:opts                  {:persistent      false
                                                 :as              :text
                                                 :decompress-body true
                                                 :body            nil
                                                 :headers         {}
                                                 :method          :get
                                                 :url             "http://pe:8080/v4/nodes"}
                         :orig-content-encoding "gzip"
                         :status                200
                         :headers               {"server" "test"}
                         :content-type          {:mime-type "application/json"}
                         :body                  " {\"test\": \"foo\"} "})

(deftest vcr-test
  (testing "VCR recording and replay"
    (let [vcr-dir "vcr-test"]
      (fs/delete-dir vcr-dir)
      (testing "when VCR is enabled"
        (let [conn (connect "http://localhost:8080" {:vcr-dir vcr-dir})]
          (is (= vcr-dir (get-in conn [:opts :vcr-dir])))
          (testing "and no recording exists"
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data
                            mock-http-response)]
              ; Real response, should be recorded
              (is (= [{:test "foo"} {"server" "test"}] (GET conn "/v4/nodes"))))
            (is (not-empty (fs/list-dir vcr-dir))))
          (testing "and a recording already exists"
            (is (= [{:test "foo"} {"server" "test"}] (GET conn "/v4/nodes"))))
          (testing "and a recording already exists and the real endpoint has changed"
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data but modified
                            (assoc mock-http-response :body " {\"test\": \"different-body-this-time\"} "))]
              ; VCR enabled so we expect to see the original body
              (is (= [{:test "foo"} {"server" "test"}] (GET conn "/v4/nodes")))))))
      (testing "when VCR is not enabled but a recording exists"
        (let [conn (connect "http://localhost:8080")]
          (is (nil? (:vcr-dir conn)))
          (with-redefs [http/get
                        (fn [& rest]
                          ; Return mock data but modified
                          (assoc mock-http-response :body " {\"test\": \"different-body-this-time\"} "))]
            ; VCR disabled so we expect to see the different body
            (is (= [{:test "different-body-this-time"} {"server" "test"}] (GET conn "/v4/nodes"))))))
      (fs/delete-dir vcr-dir))))
