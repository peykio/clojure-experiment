# Clojure Experiment

## Getting Started

...

## Clojure? 🧐

_Rich Hickey videos_

Rich Hickey On Clojure

https://www.youtube.com/watch?v=YAd6XUjutg4

Clojure Data Structures Part 1

https://www.youtube.com/watch?v=ketJlzX-254

Clojure Data Structures Part 2

https://www.youtube.com/watch?v=sp2Zv7KFQQ0

Design, Composition, and Performance

https://www.youtube.com/watch?v=QCwqnjxqfmY

Simple Made Easy

https://www.youtube.com/watch?v=LKtk3HCgTa8

The Language of the System

https://www.youtube.com/watch?v=ROor6_NGIWU

Are We There Yet

https://www.youtube.com/watch?v=ScEPu1cs4l0

Clojure (the first talk)

https://www.youtube.com/watch?v=m1tZEn_NAqg

all the rest

https://www.youtube.com/playlist?list=PLZdCLR02grLrEwKaZv-5QbUzK0zGKOOcr

Rich Already Answered That!

_A list of commonly asked questions, design decisions, reasons why Clojure is the way it is as they were answered directly by Rich._
https://gist.github.com/reborg/dc8b0c96c397a56668905e2767fd697f

_Other Clojure videos_

Clojure in the Large - Stuart Sierra

https://www.youtube.com/watch?v=1siSPGUu9JQ

#### Risks

Clojure no longer has that shiny-new-thing momentum that it had back in ~2012-2017. It seems to have found a place for itself as more of an enterprise type of language where established teams write code in private repositories. As a result there are relatively few examples of large clojure projects and not a lot of talk about it on social media.

Hiring can be an issue with Clojure - probably not at any sort of scale we are considering but might be an issue for the get a 15M series A and hire 100 people type of team.

We have no Clojure or even Java experts on the team so there is no one to ask for help. This combined with the lack of examples and there could be a lot trail and error and library code reading to figure things out.

## System Components

Library decisions that play a significant role in the architecture of the code base.

### Integrant

https://github.com/weavejester/integrant

https://www.youtube.com/watch?v=tiWTpp_DPIQ

This whole category of "lifecycle management" libraries feels very clojure-y and not really something that would come up in other languages. Although it is similar in concept to Dependency Injection (DI) like you might see in Java, the driving reason behind it is different.

https://kit-clj.github.io/docs/integrant.html

#### Risks

There isn't a lot of Integrant only code in the application so if we had to move away it wouldn't be too much work. Although we would probably still need to use a "lifecycle management" system of some kind to REPL development.

#### Alternatives

_Component_

https://github.com/stuartsierra/component

The first lifecycle library - also came up with the reloaded workflow to use the repl effectively. People complain that because it uses records rather than maps it is too java-y, not functional and requires too many Component specific changes to the code.

https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded

https://www.youtube.com/watch?v=13cmHf_kt-Q

_Mount_

https://github.com/tolitius/mount

### Pathom

https://pathom3.wsscode.com/
https://github.com/wilkerlucio/pathom3

A GraphQL alternative that uses Datalog to create queries and has a more flexible graph structure where resolvers are connected via input and outputs rather than the predetermined model used in GraphQL.

A bunch of videos talking about both Pathom and GraphQL in general:
https://pathom3.wsscode.com/media

_Pathom Viz_

https://github.com/wilkerlucio/pathom-viz
https://roamresearch.com/#/app/wsscode/page/RG9C93Sip

It also has a GraphiQL equivalent called PathomViz that gives auto-completion and queries response timings.

_Using with GraphQL_

We don't want to lose the GraphQL eco system so we could probably want to look into either merging a GraphQL api into Pathom with the [Pathom-GraphQL integration](https://pathom3.wsscode.com/docs/integrations/graphql) or create a GraphQL api out of Pathom directly using the [Pathom-Lacinia integration](https://github.com/denisidoro/graffiti).

#### Why

Why would be want to use Pathom over just staying with GraphQL? Pathom has a much more flexible graph design where entities are linked by their inputs and outputs. Queries can be added at any point in the graph if they are getting the required inputs which means you don't special syntax for things like fragments. For example if a Volume entity output a file_id and a File entity required a file_id input you don't need to add a files field to Volume you can just add the files to your query and pathom will link it up for you.

#### Risks

Right now it is mainly being developed by 1 person - https://github.com/wilkerlucio - so if he gets bored of the project it may have trouble picking up momentum again.

That being said - we would still be creating a graph api with entities and edges so if we had to drop Pathom and move back to GraphQL we wouldn't have to rethink how the api is structured.

Not sure how much you can lock down the API in production - does it have something like persisted queries so we don't have to expose the graph in production?

#### Alternatives

_Lacinia_

https://lacinia.readthedocs.io/en/latest/
https://github.com/walmartlabs/lacinia

We could just use GrahpQL directly instead of Pathom. Lacinia is a library for implementing Facebook’s GraphQL specification in idiomatic Clojure. It is created and maintained by Walmart Labs and has been used in production for like 5+ years so it is very stable.

### Pedestal

http://pedestal.io/
https://github.com/pedestal/pedestal

Pedestal is a set of libraries written in Clojure that aims to bring both the language and its principles (Simplicity, Power and Focus) to server-side development.

Pedestal is a little different from other server-side frameworks that we might be used in that it uses an interceptor pattern rather than the more commonly used middleware pattern. Interceptors seem very flexible and powerful but since it is a different coding style it might be difficult for people to understand.

http://pedestal.io/reference/interceptors

https://github.com/pedestal/pedestal#notable-capabilities

Faster Delivery with Pedestal and Vase
_vase didn't seem to turn into anything but the interceptors are still core to pedestal_
https://www.youtube.com/watch?v=_Cf-STRvFy8

#### Alternatives

_Ring_

https://github.com/ring-clojure/ring

Ring is very popular and most examples/project use ring rather than pedestal

### Datomic

https://docs.datomic.com/cloud/

Datomic Cloud is a distributed database that provides ACID transactions, flexible schema, powerful Datalog queries, complete data history, and SQL analytics support.

Datomic's data model - based on immutable s stored over time - enables a physical design that is fundamentally different from traditional RDBMSs.

_Datalog_

https://docs.datomic.com/cloud/whatis/supported-ops.html#datalog

Datomic's query and rules system is an extended form of Datalog. Datalog is a deductive query system, typically consisting of:

A database of facts
A set of rules for deriving new facts from existing facts
a query processor that, given some partial specification of a fact or rule:
finds all instances of that specification implied by the database and rules
i.e. all the matching facts

Typically a Datalog system would have a global fact database and set of rules. Datomic's query engine instead takes databases (or other data sources) as fact sources and rule sets as inputs.

Datomic Datalog
https://www.youtube.com/watch?v=bAilFQdaiHk

http://www.learndatalogtoday.org/
https://max-datom.com/

_videos_

Intro to Datomic
https://www.youtube.com/watch?v=R6ObrDWTlYA

The Design of Datomic - Rich Hickey
https://www.youtube.com/watch?v=Pz_NvY1kw6I

Writing Datomic in Clojure - Rich Hickey
https://www.youtube.com/watch?v=7Fi-UvrRpyI

Deconstructing the Database
https://www.youtube.com/watch?v=YDlhiFbrXsY

The Database as a Value
https://www.youtube.com/watch?v=D6nYfttnVco

The Functional Database - Rich Hickey
https://www.youtube.com/watch?v=tRoVyblAGrs

Datomic Cloud Tutorials
https://www.youtube.com/watch?v=qgXkowPv-uE&list=PLZdCLR02grLpRgqU50KY3YfMePw1SHnpp

Day of Datomic Cloud, Sept 2018
https://www.youtube.com/watch?v=yWdfhQ4_Yfw&list=PLZdCLR02grLoMy4TXE4DZYIuxs3Q9uc4i

Datomic Made Easy Datomic in the Cloud
https://www.youtube.com/watch?v=Ljvhjei3tWU

From REST to CQRS with Clojure, Kafka, & Datomic
https://www.youtube.com/watch?v=qDNPQo9UmJA

Simplifying ETL with Clojure and Datomic
https://www.youtube.com/watch?v=oOON--g1PyU

Datomic - a scalable, immutable database system by Marek Lipert
https://www.youtube.com/watch?v=xGrCsIiiTUs

#### Risks

Datomic is a major risk/reward decision for the application. There are a number is serious risks to using Datomic over something like Postgres.

1. Much smaller user base. Postgres is ubiquitous in the industry and you will always find experienced users or tutorials or examples or plugins to do almost anything you need.
1. _Much_ smaller user base. Clojure is already a small user base and Datomic users are a smaller fraction of that so finding open source examples or experienced users is very difficult.
1. Datomic has a SQL interface for analytics but for the most part we lose all the tooling that has built up around SQL databases.
1. Moving off of Datomic would required basically a complete rewrite of the data models and queries. Given Datomic is much more flexible than a relational SQL data model we may need to completely re-think how we organize our data.

#### Alternatives

_XTDB_

There is actually another temporal database in clojure called [xtdb](https://xtdb.com/) - _previously crux_.

It uses datalog for querying like Datomic.

It is a document oriented database rather than fact based like Datomic. Inserts need to include the entire document to make an update rather than being able to just pass in a field.

It is open source and can run on top of kafka/RockDB/s3 etc:

https://docs.xtdb.com/storage/

XTDB describes itself as a temporal database and pushes bi-temporal as a major feature:

https://www.juxt.pro/blog/value-of-bitemporality
https://docs.xtdb.com/concepts/bitemporality/

### Ions

https://docs.datomic.com/cloud/ions/ions.html

Ions let you develop applications for the cloud by deploying your code to a running Datomic compute group. You can focus on your application logic, writing ordinary Clojure functions, and the ion tooling and infrastructure handles the deployment and execution details. You can leverage your code both inside Datomic transactions and queries, and from the world at large via built-in support for AWS Lambda and web services.

Datomic Ions in Seven Minutes
https://www.youtube.com/watch?v=TbthtdBw93w

Rich Hickey on Datomic Ions
https://www.youtube.com/watch?v=thpzXjmYyGk

Datomic Ions
https://www.youtube.com/watch?v=3BRO-Xb32Ic

Declarative Domain Modeling for Datomic Ion/Cloud
https://www.youtube.com/watch?v=EDojA_fahvM

Datomic Ions Hello World in 25 minutes
https://www.youtube.com/watch?v=qmadsAy2raI

## More Interesting Things

Other Clojure ideas/libraries that I think are neat that aren't in the prototype yet.

### ClojureScript (cljs)

https://clojurescript.org/

ClojureScript is a compiler for Clojure that targets JavaScript. It emits JavaScript code which is compatible with the advanced compilation mode of the Google Closure optimizing compiler.

#### React

React was made the defacto standard for cjs libraries by being the first js framework that worked with a functional programming style. There are a bunch to libraries to make using React is cljs easier.

[reagent](https://reagent-project.github.io/)

Reagent provides a minimalistic interface between ClojureScript and React. It allows you to define efficient React components using nothing but plain ClojureScript functions and data, that describe your UI using a Hiccup-like syntax.

[re-frame](https://github.com/Day8/re-frame)

re-frame is a ClojureScript framework for building user interfaces. It has a data-oriented, functional design. Its primary focus is on high programmer productivity and scaling up to larger Single-Page applications.

[helix](https://github.com/lilactown/helix)

ClojureScript optimized for modern React development.

[uix](https://github.com/pitch-io/uix)

UIx is a ClojureScript library. It is a wrapper for React that provides idiomatic interface into modern React. Used by https://pitch.com

### nbb

https://github.com/babashka/nbb

Nbb's main goal is to make it easy to get started with ad hoc CLJS scripting on Node.js.

nbb: ad-hoc scripting for Clojure on Node.js

https://www.youtube.com/watch?v=7DQ0ymojfLg

### babashka

https://babashka.org/

Fast native Clojure scripting runtime. Avoid switching between Clojure and bash scripts. Enjoy your parens on the command lin

Babashka and Small Clojure Interpreter

https://www.youtube.com/watch?v=Nw8aN-nrdEk

### tech.ml.dataset

https://github.com/techascent/tech.ml.dataset

tech.ml.dataset is a Clojure library for data processing and machine learning. Datasets are currently in-memory columnwise databases and we support parsing from file or input-stream. We support these formats: raw/gzipped csv/tsv, xls, xlsx, json, and sequences of maps as input sources. SQL and Clojurescript bindings are provided as separate libraries.

High Performance Data With Clojure

https://www.youtube.com/watch?v=5mUGu4RlwKE

### Clerk

Clerk takes a Clojure namespace and turns it into a notebook. Computational notebooks allow arguing from evidence by mixing prose with executable code. For a good overview of problems users encounter in traditional notebooks like Jupyter.

https://github.com/nextjournal/clerk

https://nextjournal.github.io/clerk-demo/

Clerk: Local-First Notebooks for Clojure

https://www.youtube.com/watch?v=3ANS2NTNgig

### Meander

https://github.com/noprompt/meander

Meander is a Clojure/ClojureScript library that empowers you to write transparent data transformation code that allows you to plainly see the input and output of these transformations.

Meander: Declarative Explorations at the Limits of FP

https://www.youtube.com/watch?v=9fhnJpCgtUw

## Type checks, Specifications

### clojure.spec

https://clojure.org/about/spec

https://clojure.org/guides/spec

The basic idea is that specs are nothing more than a logical composition of predicates. At the bottom we are talking about the simple boolean predicates you are used to like int? or symbol?, or expressions you build yourself like #(< 42 % 66). spec adds logical ops like spec/and and spec/or which combine specs in a logical way and offer deep reporting, generation and conform support and, in the case of spec/or, tagged returns.

clojure.spec - Rich Hickey

https://www.youtube.com/watch?v=dtGzfYvBn3w

Spec-ulation Keynote - Rich Hickey

https://www.youtube.com/watch?v=oyLBGkS5ICk

### Malli

https://github.com/metosin/malli

We are building dynamic multi-tenant systems where data models should be first-class: they should drive the runtime value transformations, forms and processes. We should be able to edit the models at runtime, persist them and load them back from a database and over the wire, for both Clojure and ClojureScript. Think of JSON Schema, but for Clojure/Script.

Spec is the de facto data specification library for Clojure. It has many great ideas, but it is opinionated with macros, global registry, and it doesn't have any support for runtime transformations

Designing Clojure with Malli - Tommi Reiman

https://www.youtube.com/watch?v=bQDkuF6-py4

"Malli: Inside Data-driven Schemas" by Tommi Reiman

https://www.youtube.com/watch?v=MR83MhWQ61E
