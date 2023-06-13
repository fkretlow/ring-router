(ns com.fkretlow.ring-router-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.fkretlow.ring-router :refer [compile-router]]))

(defn- GET [uri] {:uri uri, :request-method :get})
(defn- POST [uri] {:uri uri, :request-method :post})

(defn- handler-fn [handler-id]
  (fn [request]
    (cond-> {:handler-id handler-id}
      (:params request) (assoc :params (:params request)))))

(deftest test-compile-and-dispatch
  (let [route-tree ["/" :get (handler-fn "GET /")
                    ["/items"
                     :get (handler-fn "GET /items"),
                     :post (handler-fn "POST /items")
                     ["/{id}"
                      :get (handler-fn "GET /items/{id}")
                      :put (handler-fn "PUT /items/{id}")]]
                    ["/users/{id}/details"
                     :get (handler-fn "GET /users/{id}/details")]
                    ["/groups/{group-id}/members/{member-id}"
                     :get (handler-fn "GET /groups/{group-id}/members/{member-id}")]
                    ["/api/v1"
                     ["/items" :get (handler-fn "GET /api/v1/items")]]]
        dispatch (compile-router route-tree)]

    (testing "simple get request"
      (is (= {:handler-id "GET /items"}
             (dispatch (GET "/items")))))

    (testing "get request with route param"
      (is (= {:params {:id "123"},
              :handler-id "GET /items/{id}"}
             (dispatch (GET "/items/123")))))

    (testing "concatenated route segments"
      (is (= {:handler-id "GET /api/v1/items"}
             (dispatch (GET "/api/v1/items")))))

    (testing "concatenated route segments with param"
      (is (= {:params {:id "123"},
              :handler-id "GET /users/{id}/details"}
             (dispatch (GET "/users/123/details")))))

    (testing "multiple route params are correctly mapped"
      (is (= {:params {:group-id "123", :member-id "456"},
              :handler-id "GET /groups/{group-id}/members/{member-id}"}
             (dispatch (GET "/groups/123/members/456")))))

    (testing "throws when no matching route exists"
      (is (thrown? clojure.lang.ExceptionInfo (dispatch (GET "/banana")))))))

(deftest test-multiple-route-trees
  (let [route-trees [["/items" :post (handler-fn 1)] ["/items" :post (handler-fn 2)]]
        dispatch (apply compile-router route-trees)]
    (testing "later handlers overwrite previous ones for the same route"
      (is (= {:handler-id 2}
             (dispatch (POST "/items")))))))

(deftest test-compilation-failure
  (is (thrown? java.lang.AssertionError (compile-router ["/" (handler-fn :oops)])))
  (is (thrown? java.lang.AssertionError (compile-router [:root :get (handler-fn :oops)]))))
