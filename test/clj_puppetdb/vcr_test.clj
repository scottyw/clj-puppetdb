(ns clj-puppetdb.vcr-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]
            [clj-puppetdb.http :refer :all]
            [clj-puppetdb.query :as q]
            [puppetlabs.http.client.sync :as http]
            [me.raynes.fs :as fs]))

(def mock-http-response-template {:opts                  {:persistent      false
                                                          :as              :text
                                                          :decompress-body true
                                                          :body            nil
                                                          :headers         {}
                                                          :method          :get
                                                          :url             "http://pe:8080/v4/nodes"}
                                  :orig-content-encoding "gzip"
                                  :status                200
                                  :headers               {"server" "test"}
                                  :content-type          {:mime-type "application/json"}})

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
                            (assoc mock-http-response-template :body " {\"test\": \"all-nodes\"} "))]
              ; Real response, should be recorded
              (is (= [{:test "all-nodes"} {"server" "test"}] (GET conn "/v4/nodes"))))
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data
                            (assoc mock-http-response-template :body " {\"test\": \"some-nodes\"} "))]
              ; Real response, should be recorded
              (is (= [{:test "some-nodes"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                   {:query    (q/query->json [:= :certname "test"])
                                                                    :order-by "something"}))))
            ; There should be 2 recordings
            (is (= 2 (count (fs/list-dir vcr-dir)))))
          (testing "and a recording already exists"
            (is (= [{:test "all-nodes"} {"server" "test"}] (GET conn "/v4/nodes")))
            (is (= [{:test "some-nodes"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                 {:query    (q/query->json [:= :certname "test"])
                                                                  :order-by "something"})))
            (is (= [{:test "some-nodes"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                 {:order-by "something"
                                                                  :query    (q/query->json [:= :certname "test"])}))))
          (testing "and a recording already exists and the real endpoint has changed"
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data but modified
                            (assoc mock-http-response-template :body " {\"test\": \"different-body-this-time\"} "))]
              ; VCR enabled so we expect to see the original bodies
              (is (= [{:test "all-nodes"} {"server" "test"}] (GET conn "/v4/nodes")))
              (is (= [{:test "some-nodes"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                   {:order-by "something"
                                                                    :query    (q/query->json [:= :certname "test"])})))
              (is (= [{:test "some-nodes"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                   {:query    (q/query->json [:= :certname "test"])
                                                                    :order-by "something"}))))))
        (testing "when VCR is not enabled but a recording exists"
          (let [conn (connect "http://localhost:8080")]
            (is (nil? (:vcr-dir conn)))
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data
                            (assoc mock-http-response-template :body " {\"test\": \"all-nodes-changed\"} "))]
              ; Real response, should not be recorded
              (is (= [{:test "all-nodes-changed"} {"server" "test"}] (GET conn "/v4/nodes"))))
            (with-redefs [http/get
                          (fn [& rest]
                            ; Return mock data
                            (assoc mock-http-response-template :body " {\"test\": \"some-nodes-changed\"} "))]
              ; Real response, should not be recorded
              (is (= [{:test "some-nodes-changed"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                           {:order-by "something"
                                                                            :query    (q/query->json [:= :certname "test"])})))
              (is (= [{:test "some-nodes-changed"} {"server" "test"}] (GET conn "/v4/nodes"
                                                                           {:order-by "something"
                                                                            :query    (q/query->json [:= :certname "test"])}))))
            ; There should still be just 2 recordings
            (is (= 2 (count (fs/list-dir vcr-dir)))))
          (fs/delete-dir vcr-dir))))))
