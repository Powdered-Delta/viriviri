package com.m0e_n00b.spatialworkbench.core

/*
 * Three independent group domains (design §4.1, R4.4):
 *
 *   ComponentGroupSpec  UI composition        -> ComponentCompositionResolver
 *   GrabGroupSpec       spatial manipulation  -> GrabGroupRegistry (app adapter)
 *   DanmakuSurfaceGroup danmaku render target -> DanmakuGroupAllocator
 *
 * They have separate id spaces, separate validation and separate owners and must never be merged
 * into one generic group list. An entity may be a member of a UI group and a follower of a grab
 * group at the same time; that does not make the member tables interchangeable.
 */

/** Official contract name for the UI composition group (R4.1). */
typealias ComponentGroupSpec = ComponentGroup

/** How a grab group propagates the handle motion to a member. */
enum class GrabFollowMode {
  TRANSLATION_ONLY,
  TRANSLATION_AND_ROTATION,
  FOLLOW_NONE,
}

/** How a grab group moves its members. */
enum class GrabMechanism {
  /**
   * Members are `TransformParent`-parented to the handle and the SDK is *expected* to propagate the
   * motion. The parent/child structure is provable from source; the SDK propagation is **not**
   * verified on device (design §4.3, §0.5). Until a headset verification record exists the
   * validator reports `UnverifiedGrabMechanism` as a warning, never as a silent pass.
   */
  NATIVE_PARENT,

  /** `GrabPilotSystem` applies an incremental mirror; members may live under different parents. */
  DELTA_MIRROR,
}

/** What happens to the group when hand/controller tracking is lost mid-grab. */
enum class GrabLostTrackingPolicy {
  FREEZE_AT_LAST_POSE,
  RETURN_TO_REST_POSE,
  DISBAND_GROUP,
}

/** When a grab group is active. */
enum class GrabGroupLifecycle {
  ALWAYS,
  WHILE_WORKBENCH_VISIBLE,
  ON_DEMAND,
}

/** A group-relative rest pose for [GrabLostTrackingPolicy.RETURN_TO_REST_POSE]. */
data class PoseSpec(
    val offsetMeters: Meters3 = Meters3(),
    val rotationDegrees: EulerDegrees = EulerDegrees(),
) {
  companion object {
    val IDENTITY = PoseSpec()
  }
}

/** One follower of a grab group. The theme never holds an `Entity`; it holds a semantic reference. */
data class GrabMemberSpec(
    val entityRef: EntityRef,
    val follow: GrabFollowMode = GrabFollowMode.TRANSLATION_AND_ROTATION,
    /** True = this member can also be grabbed on its own, not only as part of the group. */
    val independentlyGrabbable: Boolean = false,
    val lockRotation: Boolean = false,
    val lockScale: Boolean = true,
)

/**
 * A spatial manipulation group (R4.2). Independent from UI composition and from danmaku render
 * targets: it only knows semantic entity references, a mechanism and a follow/lifecycle policy.
 */
data class GrabGroupSpec(
    val id: String,
    /** Grab handle entity; must be registered in the entity catalog when one is supplied. */
    val handleRef: EntityRef,
    /** Child group: a child group must not re-apply the parent group's delta. */
    val parentGroupId: String? = null,
    val mechanism: GrabMechanism = GrabMechanism.NATIVE_PARENT,
    val members: List<GrabMemberSpec>,
    val restPose: PoseSpec? = null,
    val lifecycle: GrabGroupLifecycle = GrabGroupLifecycle.ALWAYS,
    val lostTrackingPolicy: GrabLostTrackingPolicy = GrabLostTrackingPolicy.FREEZE_AT_LAST_POSE,
    /** Mutual exclusion: at most one of these groups may be grabbed at a time. Must be symmetric. */
    val exclusiveWith: Set<String> = emptySet(),
    /** While this group is grabbed, suppress grab input for other groups. */
    val suppressOthersWhileGrabbed: Boolean = true,
) {
  val memberRefs: Set<EntityRef>
    get() = members.mapTo(linkedSetOf(), GrabMemberSpec::entityRef)
}

/**
 * The default grab group. It is a *structural* replica of today's behaviour (stage + grab bar hang
 * under the workbench root through `TransformParent`), not a proven behaviour-preserving one: the
 * SDK propagation itself has no headset verification record (design §4.3, R4.5).
 */
object DefaultWorkbenchGrabGroup {
  const val ID: String = "workbench"

  fun create(): GrabGroupSpec =
      GrabGroupSpec(
          id = ID,
          handleRef = EntityRef("workbenchRoot"),
          mechanism = GrabMechanism.NATIVE_PARENT,
          members =
              listOf(
                  GrabMemberSpec(EntityRef("grabBar"), GrabFollowMode.TRANSLATION_AND_ROTATION),
                  GrabMemberSpec(EntityRef("mediaStage"), GrabFollowMode.TRANSLATION_AND_ROTATION),
              ),
          lifecycle = GrabGroupLifecycle.ALWAYS,
          lostTrackingPolicy = GrabLostTrackingPolicy.FREEZE_AT_LAST_POSE,
          suppressOthersWhileGrabbed = true,
      )
}

/** Result of resolving a UI composition group into render order. */
data class ResolvedComponentComposition(
    val groupId: String,
    /** Members in render order: declaration order, each parent before its children. */
    val orderedMembers: List<ComponentNode>,
    val issues: List<ThemeValidationIssue> = emptyList(),
)

/**
 * Pure UI composition resolver (design §4.2, §9.1). Reordering group members changes the resolved
 * render order; components outside the controlled registry are rejected when a registry is given.
 */
object ComponentCompositionResolver {
  fun resolve(
      group: ComponentGroupSpec,
      registry: ThemeComponentRegistry? = null,
  ): ResolvedComponentComposition {
    val issues = mutableListOf<ThemeValidationIssue>()
    val ordered = mutableListOf<ComponentNode>()

    fun visit(node: ComponentNode, path: String) {
      ordered += node
      val componentId = ComponentId(node.id)
      val descriptor = registry?.descriptor(componentId)
      if (registry != null && descriptor == null) {
        issues += ThemeValidationIssue.UnknownComponent(path, node.id, "$path.members[${node.id}]")
      }
      val declaredSlot =
          node.properties["slot"]?.let { name ->
            PanelSlot.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
          }
      if (group.allowedSlots.isNotEmpty() && declaredSlot != null && declaredSlot !in group.allowedSlots) {
        issues +=
            ThemeValidationIssue.ComponentSlotNotAllowed(
                group.id,
                node.id,
                declaredSlot,
                "$path.members[${node.id}].properties.slot",
            )
      }
      node.children.forEach { visit(it, path) }
      node.namedSlots.forEach { (slot, nodes) -> nodes.forEach { visit(it, "$path.members[${node.id}].$slot") } }
    }

    group.members.forEach { visit(it, "componentGroups.${group.id}") }
    return ResolvedComponentComposition(group.id, ordered, issues)
  }
}
