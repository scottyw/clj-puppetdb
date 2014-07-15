# clj-puppetdb

A Clojure library for accessing the [PuppetDB](http://docs.puppetlabs.com/puppetdb/2.1) [REST API](http://docs.puppetlabs.com/puppetdb/2.1/api/index.html).

## Usage

Well, it doesn't do much yet. The `clj-puppetdb.connect` namespace has some stuff that you can try out:

### Without SSL

Ok, if this didn't work then that would be pretty sad.

```clojure
(ns clj-puppetdb.example
  (:require [clj-puppetdb.connect :as pdb])

(def conn (pdb/connect "http://puppetdb:8080"))

(pdb/get conn "/v3/facts")
```

### With SSL (using Puppet's certificates)

In case you want to connect to PuppetDB more securely, clj-puppetdb supports using
SSL certificates. If you're running clj-puppetdb from a node that's managed by Puppet, you'll already have the certificates you need. Just supply them in a map after the host URL:

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

(pdb/get conn "/v3/facts")
```

A couple of things to note here:

- The certs are located under Puppet's SSLDIR, the location of which varies. Use `sudo puppet config print ssldir` to find the location on your system.
- The certs must be supplied as instances of `java.io.File`, not simply paths/strings.
- If you're running clj-puppetdb on a node that isn't managed by Puppet, grab the files from a node that is. It'll still work.

## Upcoming Features

Here are some things that I'm working on that will hopefully make this library worth using:

- Write PuppetDB queries in a more natural, Clojure-y way: `(query (= :certname "clojure"))` => `["=" ["certname", "clojure"]]`. (See `clj-puppetdb.query` for progress)
- Retrieve large results sets as a lazy-seq, paging automatically and transparently when necessary. (See `clj-puppetdb.paging` for progress)


## License

Copyright © 2014 Justin Holguin
