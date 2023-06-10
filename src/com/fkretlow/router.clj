(ns com.fkretlow.router
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(s/def ::uri-segment (s/and string? #(re-matches #"([\w-]+|\{[\w-_?]+\})" %)))
(s/def ::path (s/nilable (s/and sequential? (s/coll-of ::uri-segment))))

(def ^:private http-methods #{:get :post :put :delete :options})
(s/def ::method http-methods)

(s/def ::handler fn?)

(s/def ::param-keys (s/and vector? (s/coll-of keyword?)))

(defn- valid-route-tree? [route-tree]
  (or (empty? route-tree)
      (and (sequential? route-tree)
           (string? (first route-tree))
           (str/starts-with? (first route-tree) "/")
           (loop [parts (next route-tree)]
             (cond
               (contains? http-methods (first parts))
               (and (fn? (second parts))
                    (recur (nnext parts)))

               (sequential? (first parts))
               (and (valid-route-tree? (first parts))
                    (recur (next parts)))

               (nil? parts) true

               :else false)))))

(s/def ::route-tree valid-route-tree?)
(s/def ::route (s/keys :req [::path ::method ::handler]))

(s/def ::handlers (s/map-of ::method (s/keys :req [::handler] :opt [::param-keys])))
(s/def ::children (s/map-of #(or (s/valid? ::uri-segment %) (= ::param %)) ::node))
(s/def ::node (s/or :empty empty? :filled (s/keys :req [(or ::handlers ::children)])))

(defn- is-param? [uri-segment] (some? (re-matches #"\{[\w-_?]+\}" uri-segment)))
(defn- get-param-key [uri-segment] (keyword (second (re-matches #"\{([\w-_?]+)\}" uri-segment))))
(defn- split-uri [uri] (vec (remove empty? (str/split uri #"/"))))

(defn- insert-route
  ([node route] (insert-route node route []))

  ([node {::keys [path method handler], :as route} param-keys]
   {:pre [(s/valid? ::node node)
          (s/valid? ::route route)
          (s/valid? ::param-keys param-keys)]
    :post [(s/valid? ::node %)]}

   (cond
     (empty? path)
     (assoc-in node [::handlers method] {::handler handler, ::param-keys param-keys})

     (is-param? (first path))
     (let [param-key (get-param-key (first path))
           child (get-in node [::children ::param] {})]
       (assoc-in node
                 [::children ::param]
                 (insert-route child (update route ::path rest) (conj param-keys param-key))))

     :else
     (let [child (get-in node [::children (first path)] {})]
       (assoc-in node
                 [::children (first path)]
                 (insert-route child (update route ::path rest) param-keys))))))

(defn- route-tree->routes [[uri-prefix & parts, :as route-tree]]
  {:pre [(s/valid? ::route-tree route-tree)]
   :post [(s/valid? (s/coll-of ::route) %)]}

  (when (seq route-tree)
    (let [path (split-uri uri-prefix)]
      (loop [parts parts
             routes []]
        (cond
        ;; :get fn ...
          (contains? http-methods (first parts))
          (let [[method handler & more] parts]
            (recur
             more
             (conj routes {::path path, ::method method, ::handler handler})))

        ;; ["/abc" ...] ...
          (sequential? (first parts))
          (recur
           (next parts)
           (concat routes
                   (map
                    (fn [route] (update route ::path #(concat path %)))
                    (route-tree->routes (first parts)))))

          (nil? parts)
          routes)))))

(defn- insert-route-tree [node route-tree]
  {:pre [(s/valid? ::node node)
         (s/valid? ::route-tree route-tree)]
   :post [(s/valid? ::node node)]}

  (let [routes (route-tree->routes route-tree)]
    (reduce (fn [router route] (insert-route router route)) node routes)))

(defn- error-no-matching-route! [request]
  (throw (ex-info "no matching route" request)))

(defn- dispatch-request [node {uri :uri, method :request-method, :as request}]
  (loop [node node
         path (split-uri uri)
         params []]
    (cond
      (empty? path)
      (if-let [{::keys [handler param-keys]} (get-in node [::handlers method])]
        (let [route-params (apply merge (:route-params request) (map vector param-keys params))]
          (handler (-> request
                       (assoc :route-params route-params)
                       (update :params #(merge % route-params)))))
        (error-no-matching-route! request))

      (contains? (::children node) (first path))
      (recur (get-in node [::children (first path)]) (rest path) params)

      (contains? (::children node) ::param)
      (recur (get-in node [::children ::param]) (rest path) (conj params (first path)))

      :else
      (error-no-matching-route! request))))

(defn compile-router
  "Given any number of `route-trees`, compile a dispatch function that dispatches
  a ring request to the appropriate handler function.

  Route trees are (potentially nested) vectors with the following grammar:

    `route-tree`: `[path-segment (method-handler-pair|route-tree)+]`
    `path-segment`: an URI segment like `\"/\"` or `\"/items/{id}\"`
    `method-handler-pair`: `method handler`
    `method`: one of `:get :post :put :delete :options`
    `handler`: the handler function for requests to this endpoint

  For example, with the following route tree, GET requests to `\"/items/123\"` will be
  handled by the function `get-item`:

    ```
    [\"/\" :get get-root,
     [\"/items\"
      :get get-items,
      :post post-items,
      [\"/{id}\" :get get-item]]]
    ```

  The compiled dispatch function throws a `clojure.lang.ExceptionInfo` with the message
  `\"no matching route\"` and the given `request` as its `:data` when no matching route
  is found, so you'll want to wrap it with some error catching middleware."
  [& route-trees]
  {:pre [(s/valid? (s/coll-of ::route-tree) route-trees)]}
  (let [tree (reduce insert-route-tree {} route-trees)]
    (partial dispatch-request tree)))
