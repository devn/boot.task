(ns tailrecursion.boot.middleware.pom
  (:require
   [tailrecursion.boot.tmpregistry :refer [mk mkdir exists? unmk]]
   [tailrecursion.boot.dispatch :as dispatch]
   [clojure.java.io :as io])
  (:import
   [org.apache.maven.model Model Repository Dependency Exclusion]
   org.apache.maven.model.io.xpp3.MavenXpp3Writer))

(defmacro dotoseq [obj seq-exprs & body]
  `(let [o# ~obj] (doseq ~seq-exprs (doto o# ~@body)) o#))

(defn ^Model set-repositories! [^Model model repositories]
  (dotoseq model [repo repositories]
    (.addRepository
     (doto (Repository.)
       (.setUrl repo)))))

(defn extract-ids [sym]
  (let [[group artifact] ((juxt namespace name) sym)]
    [(or group artifact) artifact]))

(defn ^Model set-dependencies! [^Model model dependencies]
  (dotoseq model
    [[project version & {:keys [exclusions]}] dependencies
     :let [[group artifact] (extract-ids project)]]
    (.addDependency
     (doto (Dependency.)
       (.setGroupId group)
       (.setArtifactId artifact)
       (.setVersion version)
       (.setExclusions
        (for [e exclusions :let [[group artifact] (extract-ids e)]]
          (doto (Exclusion.)
            (.setGroupId group)
            (.setArtifactId artifact))))))))

(defn ^Model build-model [boot pom]
  (let [{:keys [repositories dependencies directories]} boot
        {:keys [project version]} pom
        [group artifact] (extract-ids project)]
    (doto (Model.)
      (.setGroupId group)
      (.setArtifactId artifact)
      (.setVersion version)
      (set-repositories! repositories)
      (set-dependencies! dependencies))))

(defn contains-every? [coll ks]
  (every? identity (map contains? (repeat coll) ks)))

(defn make-pom [{:keys [boot pom]}]
  {:pre [(contains-every? pom [:project :version])]}
  (let [model (build-model boot pom)]
    {:model model
     :xml (with-out-str (.write (MavenXpp3Writer.) *out* model))}))

(defn wrap-pom [handler]
  #(handler (if (:pom %) (update-in % [:pom] merge (make-pom %)) %)))

(defmethod dispatch/dispatch-cli "pom" [env [_ path]]
  (let [pom-file (io/file (get-in env [:boot :system :cwd]) (or path "pom.xml"))]
    (spit pom-file (:xml (make-pom env)))
    (println "Wrote" (.getAbsolutePath pom-file))))
