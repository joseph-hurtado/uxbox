;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.services.queries.viewer
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [promesa.exec :as px]
   [app.common.exceptions :as ex]
   [app.common.spec :as us]
   [app.common.uuid :as uuid]
   [app.db :as db]
   [app.services.queries :as sq]
   [app.services.queries.files :as files]
   [app.services.queries.media :as media-queries]
   [app.services.queries.pages :as pages]
   [app.util.blob :as blob]
   [app.util.data :as data]))

;; --- Helpers & Specs

(s/def ::id ::us/uuid)
(s/def ::page-id ::us/uuid)

;; --- Query: Viewer Bundle (by Page ID)

(def ^:private
  sql:project
  "select p.id, p.name
     from project as p
    where p.id = ?
      and p.deleted_at is null")

(defn- retrieve-project
  [conn id]
  (db/exec-one! conn [sql:project id]))

(s/def ::share-token ::us/string)
(s/def ::viewer-bundle
  (s/keys :req-un [::page-id]
          :opt-un [::profile-id ::share-token]))

(sq/defquery ::viewer-bundle
  [{:keys [profile-id page-id share-token] :as params}]
  (db/with-atomic [conn db/pool]
    (let [page (pages/retrieve-page conn page-id)
          file (files/retrieve-file conn (:file-id page))
          images (media-queries/retrieve-media-objects conn (:file-id page) true)
          project (retrieve-project conn (:project-id file))]
      (if (string? share-token)
        (when (not= share-token (:share-token page))
          (ex/raise :type :validation
                    :code :not-authorized))
        (files/check-edition-permissions! conn profile-id (:file-id page)))
      {:page page
       :file file
       :images images
       :project project})))
