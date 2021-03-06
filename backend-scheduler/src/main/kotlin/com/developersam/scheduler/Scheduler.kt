package com.developersam.scheduler

import com.developersam.fp.FpList
import com.developersam.fp.FpMap
import com.developersam.fp.cons
import com.developersam.scheduler.SchedulerEvent.Repeats.inConfig
import com.developersam.scheduler.TaggedInterval.Type.EVENT
import com.developersam.scheduler.TaggedInterval.Type.PROJECT
import typedstore.nowInUTC
import typedstore.toLocalDateTimeInUTC
import typedstore.toUTCMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Collectors
import kotlin.math.min

/**
 * [Scheduler] is responsible for providing algorithms to automatically schedule a list of selected
 * tasks based on user supplied projects.
 *
 * @param config1 the config for the first person, which is treated as the primary user.
 * @param config2 the config for the second person, which can be omitted and then this class can be
 * used to do scheduling for only one person.
 */
class Scheduler(config1: SchedulerData, config2: SchedulerData = SchedulerData.empty) {

    /*
     * --------------------------------------------------------------------------------
     * Part 0: Initializations
     * --------------------------------------------------------------------------------
     */

    /**
     * [mergedIntervalContainersMap] is a map of all merged interval containers, with their values
     * mapped to their keys.
     */
    private val mergedIntervalContainersMap: Map<String, IntervalContainer> =
            Merger(config1, config2).mergedMap

    /**
     * [preparedIntervals] is a list of all generated intervals sorted by the finishing time.
     */
    private val preparedIntervals: List<AnnotatedInterval> = mergedIntervalContainersMap.values
            .asSequence()
            .map { it.generateIntervals() }
            .flatten()
            .sorted()
            .toList()

    /*
     * --------------------------------------------------------------------------------
     * Part 1: Class Definitions
     * --------------------------------------------------------------------------------
     */

    /**
     * [AnnotatedInterval] represents a key-ed interval with [start] and [end] and [key].
     * It also has a set of fields for weight calculation and conflicting determination, such as:
     * - [weight], which tells the weight.
     * - [isPrimaryUser], which is the user identity marker.
     * - [isGroupProject], which tells the type of the interval (shared/individual).
     * - [maxFullWeightCount], which tells the maximum number of intervals with full weight.
     */
    private data class AnnotatedInterval(
            val key: String, val start: Long, val end: Long, val weight: Long,
            val isPrimaryUser: Boolean, val isGroupProject: Boolean,
            private val maxFullWeightCount: Long
    ) : Comparable<AnnotatedInterval> {

        /**
         * [getTotalWeight] returns the total weight of the scheduler project with
         * count [timeUnitCount].
         * The receiver [SchedulerProject] is assumed to have a non-null key.
         */
        fun getTotalWeight(timeUnitCount: Int): Double {
            if (timeUnitCount <= maxFullWeightCount) {
                return (timeUnitCount * weight).toDouble()
            }
            var decayCoefficient = DECAY_RATE
            var totalWeight = (maxFullWeightCount * weight).toDouble()
            for (i in 0 until (timeUnitCount - maxFullWeightCount)) {
                totalWeight += decayCoefficient * weight
                decayCoefficient *= DECAY_RATE
            }
            return totalWeight
        }

        override fun compareTo(other: AnnotatedInterval): Int = end.compareTo(other = other.end)

    }

    /**
     * [IntervalContainer] specifies how an [IntervalContainer] implicitly containing intervals
     * should behave.
     */
    private interface IntervalContainer {

        /**
         * [key] is the key of the generator.
         */
        val key: String

        /**
         * [isPrimaryUser] returns whether this generator belongs to the primary user.
         */
        val isPrimaryUser: Boolean

        /**
         * [original] returns the original record.
         */
        val original: SchedulerRecord

        /**
         * [generateIntervals] returns a list of intervals between now and the max deadline of the
         * container.
         */
        fun generateIntervals(): List<AnnotatedInterval>
    }

    /**
     * [AnnotatedProject] is the scheduler project with string based [key] and user identity marker
     * [isPrimaryUser].
     */
    private data class AnnotatedProject(
            override val key: String, override val isPrimaryUser: Boolean,
            override val original: SchedulerProject
    ) : IntervalContainer {

        override fun generateIntervals(): List<AnnotatedInterval> {
            val minTimeUnits = 1000 * 3600 * original.minimumTimeUnits
            val now = System.currentTimeMillis()
            var end = min(a = original.deadline, b = maxDeadline)
            val list = ArrayList<AnnotatedInterval>(((end - now) / minTimeUnits + 1).toInt())
            while (end > now) {
                val start = end - minTimeUnits
                list.add(element = AnnotatedInterval(
                        key = key, start = start, end = end, weight = original.weight,
                        isPrimaryUser = isPrimaryUser, isGroupProject = original.isGroupProject,
                        maxFullWeightCount = original.estimatedTimeUnits
                ))
                end = start
            }
            list.reverse()
            return list
        }

        override fun equals(other: Any?): Boolean = when {
            this === other -> true
            other is IntervalContainer -> key == other.key
            else -> false
        }

        override fun hashCode(): Int = key.hashCode()

    }

    /**
     * [AnnotatedEvent] is the scheduler event with string based [key] and user identity marker
     * [isPrimaryUser].
     */
    private data class AnnotatedEvent(
            override val key: String, override val isPrimaryUser: Boolean,
            override val original: SchedulerEvent
    ) : IntervalContainer {

        /**
         * [LocalDate.withHourOffset] returns the [LocalDateTime] at specific [hour] of this date.
         * The hour can exceed the normal range and this method can correctly returns the day with
         * that amount of offset.
         */
        private fun LocalDate.withHourOffset(hour: Long): LocalDateTime {
            val intHour = hour.toInt()
            val standardizedHour = (intHour % 24 + 24) % 24
            val daysToAdd = (intHour - standardizedHour) / 24
            return this.plusDays(daysToAdd.toLong()).atTime(standardizedHour, 0)
        }

        override fun generateIntervals(): List<AnnotatedInterval> {
            val nowTime = System.currentTimeMillis()
            if (original.type == SchedulerEvent.EventType.ONE_TIME) {
                if (original.repeatConfig !in nowTime..maxDeadline) {
                    return emptyList()
                }
                val eventDay = toLocalDateTimeInUTC(date = original.repeatConfig).toLocalDate()
                val start = eventDay.withHourOffset(hour = original.startHour).toUTCMillis()
                val end = eventDay.withHourOffset(hour = original.endHour).toUTCMillis()
                return listOf(element = AnnotatedInterval(
                        key = key, start = start, end = end, weight = EVENT_WEIGHT,
                        isPrimaryUser = isPrimaryUser, isGroupProject = false,
                        maxFullWeightCount = EVENT_MAX_FULL_WEIGHT_COUNT
                ))
            }
            val today = nowInUTC().toLocalDate()
            val repeatConfig = original.repeatConfig
            var endDay = toLocalDateTimeInUTC(date = maxDeadline).toLocalDate()
            val intervals = arrayListOf<AnnotatedInterval>()
            while (endDay >= today) {
                val intervalOpt = if (endDay.dayOfWeek.inConfig(config = repeatConfig)) {
                    val start = endDay.withHourOffset(hour = original.startHour).toUTCMillis()
                    if (start < nowTime) null else {
                        val end = endDay.withHourOffset(hour = original.endHour).toUTCMillis()
                        AnnotatedInterval(
                                key = key, start = start, end = end, weight = EVENT_WEIGHT,
                                isPrimaryUser = isPrimaryUser, isGroupProject = false,
                                maxFullWeightCount = EVENT_MAX_FULL_WEIGHT_COUNT
                        )
                    }
                } else null
                intervalOpt?.let { intervals.add(element = it) }
                endDay = endDay.minusDays(1)
            }
            return intervals
        }

        override fun equals(other: Any?): Boolean = when {
            this === other -> true
            other is IntervalContainer -> key == other.key
            else -> false
        }

        override fun hashCode(): Int = key.hashCode()

    }

    /**
     * [Plan] is a record of a plan with a list of [intervalList].
     * It also has a [timeUnitStat] and [totalWeight] associated to the list. These values must be
     * in sync.
     */
    private inner class Plan(
            val intervalList: FpList<AnnotatedInterval> = FpList.empty(),
            val timeUnitStat: FpMap<String, Int> = FpMap.empty(), val totalWeight: Double = 0.0
    ) {

        /**
         * Construct with an [interval] only.
         */
        constructor(interval: AnnotatedInterval) : this(
                intervalList = FpList.singleton(data = interval),
                timeUnitStat = FpMap.singleton(key = interval.key, value = 1),
                totalWeight = interval.weight.toDouble()
        )

        /**
         * [canAdd] reports whether the given [interval] can be added to this plan.
         */
        private fun canAdd(interval: AnnotatedInterval): Boolean = when (intervalList) {
            FpList.Nil -> true
            is FpList.Node<AnnotatedInterval> -> intervalList.data.end <= interval.start
        }

        /**
         * [with] returns the plan with the given [interval] if it can be added, `null` if cannot.
         */
        fun with(interval: AnnotatedInterval): Plan? {
            if (!canAdd(interval = interval)) return null
            val newList = interval cons intervalList
            val key = interval.key
            val oldCount = timeUnitStat[key] ?: 0
            val newCount = oldCount + 1
            val newStat = timeUnitStat.put(key = key, value = newCount)
            val newWeight = totalWeight - interval.getTotalWeight(timeUnitCount = oldCount) +
                    interval.getTotalWeight(timeUnitCount = newCount)
            return Plan(intervalList = newList, timeUnitStat = newStat, totalWeight = newWeight)
        }

        override fun toString(): String =
                "Plan{intervals: $intervalList, timeStats: $timeUnitStat, weight: $totalWeight}"

    }

    /**
     * [PlanPair] is a pair of plans where [p1] is for user 1 and [p2] is for user 2.
     */
    private inner class PlanPair(val p1: Plan = Plan(), val p2: Plan = Plan()) {

        /**
         * [totalWeight] returns the total weight of the plan.
         */
        val totalWeight: Double get() = p1.totalWeight + p2.totalWeight

        /**
         * [with] returns the plan pair with the given [interval] if it can be added, `null` if
         * cannot.
         */
        fun with(interval: AnnotatedInterval): PlanPair? {
            return when {
                interval.isGroupProject -> {
                    val newP1 = p1.with(interval = interval) ?: return null
                    val newP2 = p2.with(interval = interval) ?: return null
                    PlanPair(p1 = newP1, p2 = newP2)
                }
                interval.isPrimaryUser -> {
                    val newP1 = p1.with(interval = interval) ?: return null
                    PlanPair(p1 = newP1, p2 = p2)
                }
                else -> {
                    val newP2 = p2.with(interval = interval) ?: return null
                    PlanPair(p1 = p1, p2 = newP2)
                }
            }
        }

        override fun toString(): String = "($p1, $p2)"

    }

    /**
     * [createPlanPair] returns an created [PlanPair] from a single [interval].
     */
    private fun createPlanPair(interval: AnnotatedInterval): PlanPair {
        val plan = Plan(interval = interval)
        return when {
            interval.isGroupProject -> PlanPair(p1 = plan, p2 = plan)
            interval.isPrimaryUser -> PlanPair(p1 = plan, p2 = Plan())
            else -> PlanPair(p1 = Plan(), p2 = plan)
        }
    }

    /*
     * --------------------------------------------------------------------------------
     * Part 2: Pre-processors
     * --------------------------------------------------------------------------------
     */

    /**
     * [Merger] is responsible for merge the raw data [config1] and [config2].
     */
    private class Merger(private val config1: SchedulerData, private val config2: SchedulerData) {

        /**
         * [merged] is the list of merged projects/events as [IntervalContainer]s.
         */
        private val merged = arrayListOf<IntervalContainer>()
        /**
         * [tentativeGroupProjects] is a list of possibly group projects.
         */
        private val tentativeGroupProjects = arrayListOf<AnnotatedProject>()

        /**
         * [classifyProjectList] transforms and adds projects in config to [merged] or
         * [tentativeGroupProjects] based on projects' properties.
         */
        private fun classifyProjectList(projects: List<SchedulerProject>, isPrimaryUser: Boolean) {
            for (project in projects) {
                val annotatedProject = project.toAnnotatedProject(isPrimaryUser = isPrimaryUser)
                if (project.isCompleted) {
                    continue
                }
                if (project.isGroupProject) {
                    tentativeGroupProjects.add(element = annotatedProject)
                } else {
                    merged.add(element = annotatedProject)
                }
            }
        }

        /**
         * [processEventList] processes the [events] to add them to [merged].
         */
        private fun processEventList(events: List<SchedulerEvent>, isPrimaryUser: Boolean) {
            for (event in events) {
                merged.add(element = event.toAnnotatedEvent(isPrimaryUser = isPrimaryUser))
            }
        }

        /**
         * [average] returns the average of [fo] and [so] based on [transform].
         */
        private inline fun average(
                fo: SchedulerProject, so: SchedulerProject,
                crossinline transform: SchedulerProject.() -> Long
        ): Long = (fo.transform() + so.transform()) / 2

        /**
         * [reconcile] tries to reconcile the difference between projects in a title group.
         */
        private fun reconcile(projects: List<AnnotatedProject>) {
            when (projects.size) {
                0, 1 -> {
                    // Not common for both
                    for (project in projects) {
                        val correctProject = project.copy(original = project.original.copy(
                                isGroupProject = false
                        ))
                        merged.add(element = correctProject)
                    }
                }
                2 -> {
                    val i1 = projects[0]
                    val i2 = projects[1]
                    val first = if (i1.isPrimaryUser) i1 else i2
                    val second = if (i1.isPrimaryUser) i2 else i1
                    val fo = first.original
                    val so = second.original
                    val reconciled = fo.copy(
                            minimumTimeUnits = average(fo, so) { minimumTimeUnits },
                            estimatedTimeUnits = average(fo, so) { estimatedTimeUnits },
                            weight = average(fo, so) { weight }
                    )
                    merged.add(reconciled.toAnnotatedProject(isPrimaryUser = true))
                }
                else -> error(message = "Impossible")
            }
        }

        init {
            // Merge
            classifyProjectList(projects = config1.projects, isPrimaryUser = true)
            classifyProjectList(projects = config2.projects, isPrimaryUser = false)
            processEventList(events = config1.events, isPrimaryUser = true)
            processEventList(events = config2.events, isPrimaryUser = false)
            val titleGroupedGroupProjects = tentativeGroupProjects.asSequence()
                    .groupBy { it.original.title }.map { it.value }
            titleGroupedGroupProjects.forEach { reconcile(projects = it) }
        }

        /**
         * [mergedMap] is a map of all [IntervalContainer]s from key to its value.
         * It can be used for quick look up.
         */
        val mergedMap: Map<String, IntervalContainer>
            get() = merged.toSet().parallelStream().collect(Collectors.toMap({ it.key }, { it }))

    }

    private companion object {

        /*
         * --------------------------------------------------------------------------------
         * Part 3: Scheduler Project/Event Extensions
         * --------------------------------------------------------------------------------
         */

        /**
         * [SchedulerProject.toAnnotatedProject] returns the corresponding [AnnotatedProject] with
         * the specified [isPrimaryUser] property.
         */
        private fun SchedulerProject.toAnnotatedProject(isPrimaryUser: Boolean): AnnotatedProject =
                AnnotatedProject(
                        key = key?.toUrlSafe()!!, isPrimaryUser = isPrimaryUser, original = this
                )

        /**
         * [SchedulerEvent.toAnnotatedEvent returns the corresponding [AnnotatedEvent] with the
         * specified [isPrimaryUser] property.
         */
        private fun SchedulerEvent.toAnnotatedEvent(isPrimaryUser: Boolean): AnnotatedEvent =
                AnnotatedEvent(
                        key = key?.toUrlSafe()!!, isPrimaryUser = isPrimaryUser, original = this
                )

        /*
         * --------------------------------------------------------------------------------
         * Part 6: Constants and Configs
         * --------------------------------------------------------------------------------
         */

        /**
         * [DECAY_RATE] represents the rate of weight decay when the estimated number of units has
         * been surpassed.
         */
        private const val DECAY_RATE: Double = 2.0 / 3

        /**
         * [EVENT_WEIGHT] is a large value to make choosing event for the algorithm mandatory.
         */
        private const val EVENT_WEIGHT: Long = 1_000_000_000

        /**
         * [EVENT_MAX_FULL_WEIGHT_COUNT] is a large value to make choosing event to always get the
         * full weight.
         */
        private const val EVENT_MAX_FULL_WEIGHT_COUNT: Long = 1_000_000_000

        /**
         * [maxDeadline] is the latest time for interval scheduling.
         */
        private val maxDeadline: Long get() = System.currentTimeMillis() + 86400_000 * 14

    }

    /*
     * --------------------------------------------------------------------------------
     * Part 7: Public APIs
     * --------------------------------------------------------------------------------
     */

    /**
     * [schedule] returns a list of [TaggedInterval] for a group in the
     * perspective of the primary user.
     */
    fun schedule(): List<TaggedInterval> {
        val len = preparedIntervals.size
        if (len == 0) {
            return emptyList()
        }
        // dp array
        val dp: Array<PlanPair> = Array(size = len) { PlanPair() }
        for (i in 0 until len) {
            val interval = preparedIntervals[i]
            var optPlanPair = createPlanPair(interval = interval) // singleton plan
            var currentBestWeight = optPlanPair.totalWeight
            for (j in 0 until i) {
                // combined with existing opt-plans
                val anotherPlanPair = dp[j].with(interval = interval) ?: continue
                val anotherPlanWeight = anotherPlanPair.totalWeight
                if (anotherPlanWeight > currentBestWeight) {
                    currentBestWeight = anotherPlanWeight
                    optPlanPair = anotherPlanPair
                }
            }
            dp[i] = optPlanPair
        }
        // extract result
        return dp.last().p1.intervalList.reverse
                .map { i ->
                    val record = mergedIntervalContainersMap[i.key]?.original
                            ?: error(message = "Impossible!")
                    val title = record.title
                    val type = if (record is SchedulerProject) PROJECT else EVENT
                    TaggedInterval(type = type, title = title, start = i.start, end = i.end)
                }
                .sorted()
                .let { TaggedInterval.mergeAdjacentInterval(intervals = it) }
    }

}
