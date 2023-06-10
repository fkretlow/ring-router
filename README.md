# Router

This is a simple routing library for use with the [Ring](https://github.com/ring-clojure/ring)
framework – no macros, no magic, just plain Clojure datastructures.

## Usage

```clojure
(ns com.example.app
  (:require [com.fkretlow.router :refer [compile-router]]
            [ring.adapter.jetty :refer [run-jetty]])

;; Assuming you have request handlers get-root, get-items etc. defined
;; and available at this point:
(def app-routes ["/" 
                 :get get-root,
                 ["/items"
                  :get get-items,
                  :post post-items,
                  ["/{id}"
                   :get get-item,
                   :delete delete-item]]])

(run-jetty (compile-router app-routes)
           {:port 3000, :join? false})
```

### Route Parameters

```clojure
;; Define a request handler that uses the route parameter :id
(defn get-item [request]
  (let [id (get-in request [:params :id])]
    (println "The id is " id)))

;; Define a route with a route parameter, using the usual notation
(def dispatch (compile-router ["/items/{id}" :get get-item])

(dispatch {:uri "/items/123", :request-method :get})
;;=> The id is 123
```


## Copyright and License

The MIT License (MIT)

Copyright © 2023 Florian Kretlow

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
