# clj-puppetdb

A Clojure library for accessing the [PuppetDB](http://docs.puppetlabs.com/puppetdb/latest) [REST API](http://docs.puppetlabs.com/puppetdb/latest/api/index.html).

[![Clojars Project](http://clojars.org/puppetlabs/clj-puppetdb/latest-version.svg)](http://clojars.org/puppetlabs/clj-puppetdb)

Highlights:

1. Supports HTTPS with [Puppet's certificates](#create-a-new-connection-with-ssl-using-puppets-certificates).
2. Provides syntactic sugar for [PuppetDB queries](#writing-puppetdb-queries).
3. Results come back as a lazy sequence of maps with keywordized keys.
4. Supports paged queries, which come back as a complete (but lazy) set of results.

## Usage

I intend for this library to be pretty simple. There are basically three things to consider: connecting, sending a request to an endpoint, and writing queries.

### Connecting

Both HTTP and HTTPS connections are supported.

#### Create a new connection without SSL

The `clj-puppetdb.core/connect` function minimally requires a host URL, including the protocol and port. An optional parameter map may be provided:

```clojure
(ns clj-puppetdb.ssl-example
  (:require [clj-puppetdb.core :as pdb]
            [clojure.java.io :as io]))

(def client (pdb/connect "http://puppetdb:8080" {:connect-timeout-milliseconds 5000}))
```
The following options are supported on both secure and unsecure connections alike:

* `:connect-timeout-milliseconds`: maximum number of milliseconds that the
  client will wait for a connection to be established.  A value of 0 is
  interpreted as infinite.  A negative value or the absence of this option
  is interpreted as undefined (system default).
* `:socket-timeout-milliseconds`: maximum number of milliseconds that the
  client will allow for no data to be available on the socket before closing the
  underlying connection, 'SO_TIMEOUT' in socket terms.  A timeout of zero is
  interpreted as an infinite timeout.  A negative value or the absence of
  this setting is interpreted as undefined (system default).

#### Create a new connection with SSL (using Puppet's certificates)

If you want to connect to PuppetDB more securely, clj-puppetdb supports using SSL certificates. If you're running clj-puppetdb from a node that's managed by Puppet, you'll already have the certificates you need. Just supply them in a map (merged with the connection parameters) after the host URL:

```clojure
(ns clj-puppetdb.ssl-example
  (:require [clj-puppetdb.core :as pdb]
            [clojure.java.io :as io]))

;; The certname for this node is "clojure"

(def certs
  {:ssl-ca-cert (io/file "/var/lib/puppet/ssl/certs/ca.pem")
   :ssl-cert (io/file "/var/lib/puppet/ssl/certs/clojure.pem")
   :ssl-key (io/file "/var/lib/puppet/ssl/private_keys/clojure.pem")})

(def client (pdb/connect "https://puppetdb:8081" certs))
```

A couple of things to note here:

* The certs are located under Puppet's SSLDIR, the location of which varies depending on OS and configuration. Use `sudo puppet config print ssldir` to find the location on your system.
* The certs must be supplied as instances of `java.io.File`, not simply paths/strings.
* If you're running clj-puppetdb on a node that isn't managed by Puppet, grab the files from a node that is. It'll still work.


#### Create a new connection with VCR support

VCR functionality is also supported in clj-puppetdb. If enabled, the first time a particular query is executed, a real
HTTP request is made to PuppetDB and the response is recorded. All future executions of the same query
result in no HTTP request with the response instead replayed from the recording. This can be useful for
testing or demo purposes as it allows clj-puppetdb to be run in a realistic manner without requiring a real PuppetDB,
after the initial recording has been made.

Enable the VCR by indicating the directory in which you'd like to store the recordings. Specifying a VCR directory
implicitly enables the VCR.

```clojure
(def client (pdb/connect "https://puppetdb:8080" {:vcr-dir "recordings/tests"}}))
```

### Simple requests

Once you've got a connection (`conn` in the below examples), you can pass it to `clj-puppetdb.core/query` along with a path:

```clojure
(pdb/query client "/v4/facts") ;; return all known facts
(pdb/query client "/v4/facts/operatingsystem") ;; return the "operatingsystem" fact for all nodes
```

The only real restriction on using the `query` function like this is that the path must be URL-encoded. You can query any version of any endpoint.

### Writing PuppetDB queries

The `query` function also supports [PuppetDB queries](http://docs.puppetlabs.com/puppetdb/latest/api/query/v4/query.html) with a tiny bit of syntactic sugar:

```clojure
;; To find all Linux nodes with more than 30 days of uptime:
(pdb/query client "/v4/nodes" [:and
                              [:= [:fact "kernel"] "Linux"]
                              [:> [:fact "uptime_days"] 30]])
```

Here are some notes/caveats:

* Keywords, symbols, strings, numbers, regex literals, etc. all work and _are generally interchangeable_. For the sake of clarity, I recommend using keywords for operators and keys.
* The `~` operator is definitely an exception. Because it's a [reader macro character](http://clojure.org/reader#The%20Reader--Macro%20characters), you can't use it in a symbol or keyword. Instead, use the string `"~"` or the keyword `:match`, which will be converted automatically.
* If you're having a hard time getting your query to work, you can use `clj-puppetdb.query/query->json` to see the JSON representation of your query vector.
* The excellent [PuppetDB Query Tutorial](http://docs.puppetlabs.com/puppetdb/latest/api/query/tutorial.html) has tons of great information and examples. Since JSON arrays are generally valid Clojure vectors, you can actually copy/paste those examples directly into a call to `clj-puppetdb.core/query`.

### Making paged queries

For queries that may return an extremely large set of results, clj-puppetdb supports [paged queries]
(http://docs.puppetlabs.com/puppetdb/latest/api/query/v4/paging.html).

#### Getting a lazy sequence of all results (implicit paging)

The `clj-puppetdb.core/lazy-query` function behaves much like `clj-puppetdb.core/query` in that it will return all
results that match the query. However this lazy version will retrieve results only one page at a time, transparently
retrieving each subsequently required page as the lazy sequence is consumed. You must supply a map containing the
`:limit` and `:order-by`/`:order_by` keys that dictate where the lazy sequence starts and what the underlying page size should be.

Here's a simple example of a lazy query:

```clojure
(ns clj-puppetdb.paging-example
  (:require [clj-puppetdb.core :as pdb]))

(def certs
  {:ssl-ca-cert (io/file "/var/lib/puppet/ssl/certs/ca.pem")
   :ssl-cert (io/file "/var/lib/puppet/ssl/certs/clojure.pem")
   :ssl-key (io/file "/var/lib/puppet/ssl/private_keys/clojure.pem")}

(def client (pdb/connect "https://puppetdb:8081" certs))

(def lazy-facts
  (pdb/lazy-query client "/v4/facts"
    {:limit 500 :offset 0 :order-by [{:field :name :order "asc"}]}))
```

The map of parameters at the end of the call to `lazy-query` is worth unpacking a bit:
- The `:limit` key is **required**, and determines how many results to return at a time.
- The `:offset` key is optional (default is 0) and determines how many results to skip at the beginning of the query.
- The `:order-by` (PuppetDB v3 API and earlier) or `:order_by` (PuppetDB v4 API and later) key is **required**, and sorts the results on the server-side. It consists of a vector of maps, where each map specifies a `:field` to sort by (e.g., `:certname` or `:value`) and either `"asc"` for ascending order or `"desc"` for descending order.

You can also supply a query vector:

```clojure
(def lazy-linux-nodes
  (pdb/lazy-query client "/v4/nodes" [:= [:fact "kernel"] "Linux"]
    {:limit 500 :order-by [{:field :certname :order "asc"}]}))
```

#### Getting a single page of results (explicit paging)

The `clj-puppetdb.core/query-with-metadata` function also behaves like `clj-puppetdb.core/query` but takes an additional
argument that allows tou to specify paging parameters. This function always returns a vector of size two where:
 - The first element contains the results
 - The second contains a map of metadata - currently just the total number of records available


The map of parameters at the end of the call to `query-with-metadata` takes similar values to `lazy-query` above:
 - The `:limit` key is **required**, and determines how many results to return at a time.
 - The `:offset` key is optional (default is 0) and determines how many results to skip at the beginning of the query.
 - The `:order-by` (PuppetDB v3 API and earlier) or `:order_by` (PuppetDB v4 API and later) key is **required**, and sorts the results on the server-side. It consists of a vector of maps, where each map specifies a `:field` to sort by (e.g., `:certname` or `:value`) and either `"asc"` for ascending order or `"desc"` for descending order.
 - The `:include-total`/`:include_total` key is optional and if true will return the total number of available records in the second element of the result vector.

Here's a simple example:

```clojure
(ns clj-puppetdb.paging-example
  (:require [clj-puppetdb.core :as pdb]))

(def client (pdb/connect "http://puppetdb:8080"))

(let [[reports metadata] (pdb/query-with-metadata client "/v4/reports"
    {:limit 3 :offset 0 :include_total true 
     :order_by [{:field :receive_time :order "asc"}]})]
    (println reports metadata))
```


You can also supply a query vector:

```clojure
(pdb/query-with-metadata client "/v4/reports" [:= :hash "aabbccdd"]
    {:limit 3 :offset 0 :order_by [{:field :name :order "asc"})
```

## Possible Future Features

Here are some things that will hopefully make this library a bit more robust:

* Handle HTTP(S) connections a bit better. Cache the certificates for SSL, do timeouts properly, etc.
* Validate queries before sending them off to the server.

## License

Apache License Version 2.0

## Support

Please log tickets and issues at our [JIRA tracker](http://tickets.puppetlabs.com).  A [mailing
list](https://groups.google.com/forum/?fromgroups#!forum/puppet-users) is
available for asking questions and getting help from others. In addition there
is an active #puppet channel on Freenode.

We use semantic version numbers for our releases, and recommend that users stay
as up-to-date as possible by upgrading to patch releases and minor releases as
they become available.

Bugfixes and ongoing development will occur in minor releases for the current
major version. Security fixes will be backported to a previous major version on
a best-effort basis, until the previous major version is no longer maintained.

For example: If a security vulnerability is discovered in version 4.1.1, we
would fix it in the 4 series, most likely as 4.1.2. Maintainers would then make
a best effort to backport that fix onto the latest version 3 release.

Long-term support, including security patches and bug fixes, is available for
commercial customers. Please see the following page for more details:

[Puppet Enterprise Support Lifecycle](http://puppetlabs.com/misc/puppet-enterprise-lifecycle)

Copyright © 2014-2015 Justin Holguin / Puppet Labs
