;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.main.ui.workspace.shapes
  "A workspace specific shapes wrappers."
  (:require
   [rumext.alpha :as mf]
   [beicon.core :as rx]
   [app.main.streams :as ms]
   [app.main.ui.hooks :as hooks]
   [app.main.ui.cursors :as cur]
   [app.main.ui.shapes.rect :as rect]
   [app.main.ui.shapes.circle :as circle]
   [app.main.ui.shapes.icon :as icon]
   [app.main.ui.shapes.image :as image]
   [app.main.data.workspace.selection :as dws]
   [app.main.store :as st]

   ;; Shapes that has some peculiarities are defined in its own
   ;; namespace under app.ui.workspace.shapes.* prefix, all the
   ;; others are defined using a generic wrapper implemented in
   ;; common.
   [app.main.ui.workspace.shapes.bounding-box :refer [bounding-box]]
   [app.main.ui.workspace.shapes.common :as common]
   [app.main.ui.workspace.shapes.frame :as frame]
   [app.main.ui.workspace.shapes.group :as group]
   [app.main.ui.workspace.shapes.path :as path]
   [app.main.ui.workspace.shapes.text :as text]
   [app.common.geom.shapes :as geom]))

(declare group-wrapper)
(declare frame-wrapper)

(def circle-wrapper (common/generic-wrapper-factory circle/circle-shape))
(def icon-wrapper (common/generic-wrapper-factory icon/icon-shape))
(def image-wrapper (common/generic-wrapper-factory image/image-shape))
(def rect-wrapper (common/generic-wrapper-factory rect/rect-shape))

(defn- shape-wrapper-memo-equals?
  [np op]
  (let [n-shape (unchecked-get np "shape")
        o-shape (unchecked-get op "shape")
        n-frame (unchecked-get np "frame")
        o-frame (unchecked-get op "frame")]
    ;; (prn "shape-wrapper-memo-equals?" (identical? n-frame o-frame))
    (if (= (:type n-shape) :group)
      false
      (and (identical? n-shape o-shape)
           (identical? n-frame o-frame)))))

(defn use-mouse-enter
  [{:keys [id] :as shape}]
  (mf/use-callback
   (mf/deps id)
   (fn []
     (st/emit! (dws/change-hover-state id true)))))

(defn use-mouse-leave
  [{:keys [id] :as shape}]
  (mf/use-callback
   (mf/deps id)
   (fn []
     (st/emit! (dws/change-hover-state id false)))))

(mf/defc shape-wrapper
  {::mf/wrap [#(mf/memo' % shape-wrapper-memo-equals?)]
   ::mf/wrap-props false}
  [props]
  (let [shape (unchecked-get props "shape")
        frame (unchecked-get props "frame")
        shape (geom/transform-shape frame shape)
        opts #js {:shape shape
                  :frame frame}
        alt? (mf/use-state false)
        on-mouse-enter (use-mouse-enter shape)
        on-mouse-leave (use-mouse-leave shape)]

    (hooks/use-stream ms/keyboard-alt #(reset! alt? %))

    (mf/use-effect
     (fn []
       (fn []
         (on-mouse-leave))))

    (when (and shape (not (:hidden shape)))
      [:g.shape-wrapper {:on-mouse-enter on-mouse-enter
                         :on-mouse-leave on-mouse-leave
                         :style {:cursor (if @alt? cur/duplicate nil)}}
       (case (:type shape)
         :curve [:> path/path-wrapper opts]
         :path [:> path/path-wrapper opts]
         :text [:> text/text-wrapper opts]
         :group [:> group-wrapper opts]
         :icon [:> icon-wrapper opts]
         :rect [:> rect-wrapper opts]
         :image [:> image-wrapper opts]
         :circle [:> circle-wrapper opts]

         ;; Only used when drawing a new frame.
         :frame [:> frame-wrapper {:shape shape}]
         nil)
       [:& bounding-box {:shape shape :frame frame}]])))

(def group-wrapper (group/group-wrapper-factory shape-wrapper))
(def frame-wrapper (frame/frame-wrapper-factory shape-wrapper))

