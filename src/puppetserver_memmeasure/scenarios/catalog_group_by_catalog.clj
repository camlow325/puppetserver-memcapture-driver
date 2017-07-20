(ns puppetserver-memmeasure.scenarios.catalog-group-by-catalog
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby-pool-manager.jruby-schemas :as jruby-schemas]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-puppet-schemas]
            [schema.core :as schema]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log])
  (:import (java.io File)
           (com.puppetlabs.puppetserver JRubyPuppet)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-catalog-group-by-catalog-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet :- JRubyPuppet
   step-base-name :- schema/Str
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [environment-name nodes] :- memmeasure-schemas/ScenarioConfig}
   jruby-config :- jruby-schemas/JRubyConfig
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   iter :- schema/Int
   _]
  (doseq [{:keys [name expected-class-in-catalog]} nodes]
    (log/infof "Compiling catalog %d for node %s"
               (inc iter)
               name)
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-node-"
                                name
                                "-catalog-"
                                (inc iter)
                                ".json"))
                      name
                      environment-name
                      jruby-puppet-config
                      expected-class-in-catalog))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-group-by-catalog-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times for a single environment
  and JRubyPuppet."
  [{:keys [environment-name
           environment-timeout
           nodes
           num-catalogs]} :- memmeasure-schemas/ScenarioConfig
   jruby-config :- jruby-schemas/JRubyConfig
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-group-by-catalog"]
    (util/with-jruby-puppet
     jruby-puppet
     jruby-config
     jruby-puppet-config
     (scenario/run-scenario-body-over-steps
      (partial run-catalog-group-by-catalog-step
               jruby-puppet)
      step-base-name
      mem-output-run-dir
      scenario-context
      {:num-containers 1
       :num-catalogs num-catalogs
       :num-environments 1
       :environment-name environment-name
       :environment-timeout environment-timeout
       :nodes nodes}
      jruby-config
      jruby-puppet-config
      (range num-catalogs)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs in one jruby and environment, grouping by catalog"
    :fn run-catalog-group-by-catalog-scenario}])
