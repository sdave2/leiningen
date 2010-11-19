(ns leiningen.plugin
  (:use [leiningen.core :only (home-dir
                               read-project
                               abort)]
        [leiningen.uberjar :only (write-components)]
        [leiningen.deps :only (deps)]
        [leiningen.jar :only (local-repo-path
                              extract-jar
                              get-default-uberjar-name)]
        [leiningen.util.file :only (tmp-dir)]
        [clojure.java.io :only (file)])
  (:require [leiningen.install]
            [leiningen.help])
  (:import [java.util.zip ZipOutputStream]
           [java.io File FileOutputStream]))

(def plugins-path (file (home-dir) "plugins"))

(defn plugin-standalone-filename [group name version]
  (str (if group (str group "-") nil) name "-" version ".jar"))

(defn extract-name-and-group [project-name]
  ((juxt name namespace) (symbol project-name)))

;; TODO: extract shared behavior between this and the install task
(defn install
  "Download, package, and install plugin jarfile into
  ~/.lein/plugins
Syntax: lein plugin install [GROUP/]ARTIFACT-ID VERSION
  You can use the same syntax here as when listing Leiningen
  dependencies."
  [project-name version]
  (leiningen.install/install project-name version)
  (let [[name group] (extract-name-and-group project-name)
        temp-project (format "%s/lein-%s" tmp-dir (java.util.UUID/randomUUID))
        jarfile (-> (local-repo-path name (or group name) version)
                    (.replace "$HOME" (System/getProperty "user.home")))
        _ (extract-jar (file jarfile) temp-project)
        project (read-project (format "%s/project.clj" temp-project))
        standalone-filename (plugin-standalone-filename group name version)]
    (deps project)
    (with-open [out (-> (str plugins-path "/" standalone-filename)
                        (FileOutputStream.)
                        (ZipOutputStream.))]
      (let [deps (->> (.listFiles (file (:library-path project)))
                      (filter #(.endsWith (.getName %) ".jar"))
                      (cons (file jarfile)))]
        (write-components deps out)))
    (println "Created" standalone-filename)))

(defn uninstall
  "Delete the plugin jarfile
Syntax: lein plugin uninstall [GROUP/]ARTIFACT-ID VERSION"
  [project-name version]
  (let [[name group] (extract-name-and-group project-name)
        jarfile (file plugins-path
                      (plugin-standalone-filename group name version))]
    (if (.exists jarfile)
      (if (.delete jarfile)
        (println (format "Successfully uninstalled %s %s." project-name version))
        (abort (format "Failed to delete \"%s\"." (.getAbsolutePath jarfile))))
      (abort (format "Plugin \"%s %s\" doesn't appear to be installed."
                       project-name version)))))

(defn ^{:doc "Manage user-level plugins."
        :help-arglists '([subtask project-name version])
        :subtasks [#'install #'uninstall]}
  plugin
  ([] (println (leiningen.help/help-for "plugin")))
  ([_] (plugin))
  ([_ _] (plugin))
  ([subtask project-name version]
    (case subtask
      "install" (install project-name version)
      "uninstall" (uninstall project-name version)
      (plugin))))
