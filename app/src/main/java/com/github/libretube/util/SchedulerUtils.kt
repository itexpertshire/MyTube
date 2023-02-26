package com.github.libretube.util

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.github.libretube.constants.RECOMMENDATION_JOB_ID
import com.github.libretube.constants.RECOMMENDATION_JOB_INTERVAL
import com.github.libretube.services.RecommendationService
import java.util.concurrent.TimeUnit


class SchedulerUtils {


    companion object {

        private val JOB_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(RECOMMENDATION_JOB_INTERVAL.toLong())

        fun wrapper(applicationContext: Context?) {
            val jobScheduler = applicationContext?.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            val pendingJob = jobScheduler.getPendingJob(RECOMMENDATION_JOB_ID)

            if (pendingJob == null) {
                scheduleJob(applicationContext)
            } else {
                unscheduleJob(applicationContext)
            }
        }

        private fun scheduleJob(context: Context) {

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler



            val jobService = ComponentName(context, RecommendationService::class.java)
            val jobInfo = JobInfo.Builder(RECOMMENDATION_JOB_ID, jobService)
                .setPeriodic(JOB_INTERVAL_MILLIS)
                //.setMinimumLatency(1000)
                .setPersisted(true)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()

            if (jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS) {
                Log.d("Amit", "Job has been successfully scheduled")

            } else {
                Log.d("Amit", "Job scheduling error")
            }


        }

        private fun unscheduleJob(context: Context) {
            val jobScheduler = context?.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(RECOMMENDATION_JOB_ID)
            Log.d("Amit", "Job cancelled");
        }
    }


}