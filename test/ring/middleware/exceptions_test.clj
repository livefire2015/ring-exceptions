(ns ring.middleware.exceptions-test
  (:require [clojure.test :refer :all]
            [ring.middleware.exceptions :refer :all]
            [clojure.tools.logging :as log]))

(defn handler-throwable
  [req]
  (throw (RuntimeException. "Test throwable")))

(defn handler-sql-exception
  [req]
  (throw (java.sql.SQLException. (RuntimeException. "Nested Error"))))

(defn handler-malformed
  [req]
  (throw (ex-info "Test malformed" {:cause :invalid-params})))

(defn handler-graphq-errors
  [req]
  (throw (ex-info "Test graphql-errors" {:cause :graphql-errors})))

(defn handler-unauthenticated-errors [req]
  (throw (ex-info "unauthenticated" {:cause :unauthenticated})))

(defn handler-sqlexception
  [req]
  (throw (java.sql.SQLException. "Test SQLException")))

(defmacro with-printing-suppressed
  "Evaluates exprs in a context in which JVM System/err and System/out captured & discarded."
  [& body]
  `(let [baos# (java.io.ByteArrayOutputStream.)
         ps#   (java.io.PrintStream. baos#)
         s#    (new java.io.StringWriter)]
     (System/setErr ps#)
     (System/setOut ps#)
     (binding [*err* s#
               *out* s#]
       (let [result# (do ~@body)]
         (.close ps#)
         (System/setErr System/err)
         (System/setOut System/out)
         result#))))

(deftest exceptions-test
  (testing "Testing exceptions"
    (with-printing-suppressed
      (let [handler (wrap-exceptions handler-sqlexception)
            response (handler {})]
        (is (= 500 (:status response))))
      (let [handler (wrap-exceptions handler-throwable)
            response (handler {})]
        (is (= 500 (:status response))))
      (let [handler (wrap-exceptions handler-malformed)
            response (handler {})]
        (is (= 400 (:status response))))
      (let [handler (wrap-exceptions handler-unauthenticated-errors)
            response (handler {})]
        (is (= 401 (:status response))))
      (let [handler (wrap-exceptions handler-graphq-errors)
            response (handler {})]
        (is (= 200 (:status response)))
        (is (not (nil? (:errors (:body response))))))
      (let [handler (wrap-exceptions handler-sqlexception)
            response (handler {})]
        (is (= 500 (:status response)))
        (is (not (nil? (:error-id (:body response)))))))))

(defn customized-server-exception-response [e req uuid]
  (let [uri (:uri req)]
    {:status 600
     :body {:message "Customized Message"}}))

(deftest customized-handler-test
  (testing "Customized exceptions"
    (with-printing-suppressed
      (let [handler (wrap-exceptions handler-throwable {:error-fns (assoc ring.middleware.exceptions/default-error-fns
                                                                          :server-exception-response customized-server-exception-response)})
            response (handler {})]
        (println "response: " response)
        (is (= 600 (:status response)))
        (is (= "Customized Message" (:message (:body response))))))))