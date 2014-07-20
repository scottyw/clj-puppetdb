# clj-puppetdb

A Clojure library for accessing the [PuppetDB](http://docs.puppetlabs.com/puppetdb/latest) [REST API](http://docs.puppetlabs.com/puppetdb/latest/api/index.html).

Highlights:

1. Supports HTTPS with [Puppet's certificates](#create-a-new-connection-with-ssl-using-puppets-certificates).
2. Provides syntactic sugar for [PuppetDB queries](#writing-puppetdb-queries).
3. Results come back as a lazy sequence of maps with keywordized keys.

## Usage

I intend for this library to be pretty simple. There are basically three things to consider: connecting, sending a request to an endpoint, and writing queries.

### Connecting

Both HTTP and HTTPS connections are supported.

#### Create a new connection without SSL

The `clj-puppetdb.core/connect` function minimally requires a host URL, including the protocol and port:

```clojure
(ns clj-puppetdb.ssl-example
  (:require [clj-puppetdb.connect :as pdb]
            [clojure.java.io :as io]))

(def conn (pdb/connect "http://puppetdb:8080"))
```

If you're connecting over plain HTTP, no other options are supported.

#### Create a new connection with SSL (using Puppet's certificates)

If you want to connect to PuppetDB more securely, clj-puppetdb supports using SSL certificates. If you're running clj-puppetdb from a node that's managed by Puppet, you'll already have the certificates you need. Just supply them in a map after the host URL:

```clojure
(ns clj-puppetdb.ssl-example
  (:require [clj-puppetdb.connect :as pdb]
            [clojure.java.io :as io]))

;; The certname for this node is "clojure"

(def certs
  {:ssl-ca-cert (io/file "/var/lib/puppet/ssl/certs/ca.pem")
   :ssl-cert (io/file "/var/lib/puppet/ssl/certs/clojure.pem")
   :ssl-key (io/file "/var/lib/puppet/ssl/private_keys/clojure.pem")}

(def conn (pdb/connect "https://puppetdb:8081" certs))
```

A couple of things to note here:

* The certs are located under Puppet's SSLDIR, the location of which varies depending on OS and configuration. Use `sudo puppet config print ssldir` to find the location on your system.
* The certs must be supplied as instances of `java.io.File`, not simply paths/strings.
* If you're running clj-puppetdb on a node that isn't managed by Puppet, grab the files from a node that is. It'll still work.

### Simple requests

Once you've got a connection (`conn` in the below examples), you can pass it to `clj-puppetdb.core/query` along with a path:

```clojure
(pdb/query conn "/v4/facts") ;; return all known facts
(pdb/query conn "/v4/facts/clojure") ;; return facts for the node with the certname "clojure"
(pdb/query conn "/v4/facts/clojure/operatingsystem") ;; return the "operatingsystem" fact for "clojure"
```

The only real restriction on using the `query` function like this is that the path must be URL-encoded. You can query any version of any endpoint.

### Writing PuppetDB queries

The `query` function also supports [PuppetDB queries](http://docs.puppetlabs.com/puppetdb/latest/api/query/v4/query.html) with a tiny bit of syntactic sugar:

```clojure
;; To find all Linux nodes with more than 30 days of uptime:
(pdb/query conn "/v4/nodes" [:and
                              [:= [:fact "kernel"] "Linux"]
                              [:> [:fact "uptime_days"] 30]])
```

Here are some notes/caveats:

* Keywords, symbols, strings, numbers, regex literals, etc. all work and _are generally interchangeable_. For the sake of clarity, I recommend using keywords for operators and keys.
* The `~` operator is definitely an exception. Because it's a [reader macro character](http://clojure.org/reader#The%20Reader--Macro%20characters), you can't use it in a symbol or keyword. Instead, use the string `"~"` or the keyword `:match`, which will be converted automatically.
* If you're having a hard time getting your query to work, you can use `clj-puppetdb.query/query->json` to see the JSON representation of your query vector.
* The excellent [PuppetDB Query Tutorial](http://docs.puppetlabs.com/puppetdb/latest/api/query/tutorial.html) has tons of great information and examples. Since JSON arrays are generally valid Clojure vectors, you can actually copy/paste those examples directly into a call to `clj-puppetdb/query`.

## Planned Features

Here are some things that I'm working on that will hopefully make this library a bit more robust:

* Handle HTTP(S) connections a bit better. Cache the certificates for SSL, do timeouts properly, etc.
* Validate queries before sending them off to the server.
* Retrieve large results sets as a lazy-seq, paging automatically and transparently when necessary.

## License

Copyright © 2014 Justin Holguin
