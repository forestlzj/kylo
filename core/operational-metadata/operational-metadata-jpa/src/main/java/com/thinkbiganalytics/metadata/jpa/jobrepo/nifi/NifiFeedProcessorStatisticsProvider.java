package com.thinkbiganalytics.metadata.jpa.jobrepo.nifi;

/*-
 * #%L
 * thinkbig-operational-metadata-jpa
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.Lists;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.thinkbiganalytics.metadata.api.common.ItemLastModified;
import com.thinkbiganalytics.metadata.api.common.ItemLastModifiedProvider;
import com.thinkbiganalytics.metadata.api.jobrepo.nifi.NifiFeedProcessorStats;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import javax.inject.Inject;

/**
 * Provider for accessing the statstics for a feed and processor
 */
@Service
public class NifiFeedProcessorStatisticsProvider implements com.thinkbiganalytics.metadata.api.jobrepo.nifi.NifiFeedProcessorStatisticsProvider {

    public static String ITEM_LAST_MODIFIED_KEY = "NIFI_FEED_PROCESSOR_STATS";

    @Autowired
    private JPAQueryFactory factory;

    private NifiFeedProcessorStatisticsRepository statisticsRepository;

    private NifiEventRepository nifiEventRepository;

    @Inject
    private ItemLastModifiedProvider itemLastModifiedProvider;

    @Inject
    private NifiEventProvider nifiEventProvider;

    @Autowired
    public NifiFeedProcessorStatisticsProvider(NifiFeedProcessorStatisticsRepository repository, NifiEventRepository nifiEventRepository) {
        this.statisticsRepository = repository;
        this.nifiEventRepository = nifiEventRepository;
    }

    private String getLastModifiedKey(String clusterId) {
        if(StringUtils.isBlank(clusterId)|| "Node".equalsIgnoreCase(clusterId)){
            return ITEM_LAST_MODIFIED_KEY;
        }
        else {
            return ITEM_LAST_MODIFIED_KEY + "-" + clusterId;
        }
    }

    @Override
    public NifiFeedProcessorStats create(NifiFeedProcessorStats t) {
        NifiFeedProcessorStats stats =  statisticsRepository.save((JpaNifiFeedProcessorStats) t);
        ItemLastModified lastModified = itemLastModifiedProvider.update(getLastModifiedKey(t.getClusterNodeId()), t.getMaxEventId().toString());
        return stats;
    }

    public List<? extends JpaNifiFeedProcessorStats> findFeedProcessorStatisticsByProcessorId(String feedName, TimeFrame timeFrame) {
        DateTime now = DateTime.now();
        return findFeedProcessorStatisticsByProcessorId(feedName, timeFrame.startTimeRelativeTo(now), now);
    }

    public List<? extends JpaNifiFeedProcessorStats> findFeedProcessorStatisticsByProcessorName(String feedName, TimeFrame timeFrame) {
        DateTime now = DateTime.now();
        return findFeedProcessorStatisticsByProcessorName(feedName, timeFrame.startTimeRelativeTo(now), now);
    }

    public List<? extends JpaNifiFeedProcessorStats> findForFeedStatisticsGroupedByTime(String feedName, TimeFrame timeFrame) {
        DateTime now = DateTime.now();
        return findForFeedStatisticsGroupedByTime(feedName, timeFrame.startTimeRelativeTo(now), now);
    }


    @Override
    public List<? extends JpaNifiFeedProcessorStats> findWithinTimeWindow(DateTime start, DateTime end) {
        return statisticsRepository.findWithinTimeWindow(start, end);
    }


    private Predicate withinDateTime(DateTime start, DateTime end) {
        QJpaNifiFeedProcessorStats stats = QJpaNifiFeedProcessorStats.jpaNifiFeedProcessorStats;
        Predicate p = null;

        if (start == null && end == null) {
            return p;
        }
        if (start != null && end != null) {
            p = stats.minEventTime.goe(start).and(stats.maxEventTime.loe(end));
        } else if (start == null) {
            p = stats.maxEventTime.loe(end);
        } else if (end == null) {
            p = stats.minEventTime.goe(start);
        }
        return p;
    }


    /**
     * Find stats for a given feed between the two dates not grouped
     */
    private List<? extends JpaNifiFeedProcessorStats> findForFeed(String feedName, DateTime start, DateTime end) {
        QJpaNifiFeedProcessorStats stats = QJpaNifiFeedProcessorStats.jpaNifiFeedProcessorStats;
        Iterable<JpaNifiFeedProcessorStats> result = statisticsRepository.findAll(stats.feedName.eq(feedName).and(withinDateTime(start, end)));
        if (result != null) {
            return Lists.newArrayList(result);
        }
        return null;
    }


    @Override
    public List<? extends JpaNifiFeedProcessorStats> findFeedProcessorStatisticsByProcessorId(String feedName, DateTime start, DateTime end) {
        QJpaNifiFeedProcessorStats stats = QJpaNifiFeedProcessorStats.jpaNifiFeedProcessorStats;
        JPAQuery
            query = factory.select(
            Projections.bean(JpaNifiFeedProcessorStats.class,
                             stats.feedName, stats.processorId, stats.processorName,
                             stats.bytesIn.sum().as("bytesIn"), stats.bytesOut.sum().as("bytesOut"), stats.duration.sum().as("duration"),
                             stats.jobsStarted.sum().as("jobsStarted"), stats.jobsFinished.sum().as("jobsFinished"), stats.jobDuration.sum().as("jobDuration"),
                             stats.flowFilesStarted.sum().as("flowFilesStarted"), stats.flowFilesFinished.sum().as("flowFilesFinished"), stats.totalCount.sum().as("totalCount"),
                             stats.maxEventTime.max().as("maxEventTime"), stats.minEventTime.min().as("minEventTime"), stats.jobsFailed.sum().as("jobsFailed"),
                             stats.count().as("resultSetCount"))
        )
            .from(stats)
            .where(stats.feedName.eq(feedName)
                       .and(stats.minEventTime.goe(start)
                                .and(stats.maxEventTime.loe(end))))
            .groupBy(stats.feedName, stats.processorId, stats.processorName)
            .orderBy(stats.processorName.asc());

        return (List<JpaNifiFeedProcessorStats>) query.fetch();
    }


    @Override
    public List<? extends JpaNifiFeedProcessorStats> findFeedProcessorStatisticsByProcessorName(String feedName, DateTime start, DateTime end) {
        QJpaNifiFeedProcessorStats stats = QJpaNifiFeedProcessorStats.jpaNifiFeedProcessorStats;
        JPAQuery
            query = factory.select(
            Projections.bean(JpaNifiFeedProcessorStats.class,
                             stats.feedName, stats.processorName,
                             stats.bytesIn.sum().as("bytesIn"), stats.bytesOut.sum().as("bytesOut"), stats.duration.sum().as("duration"),
                             stats.jobsStarted.sum().as("jobsStarted"), stats.jobsFinished.sum().as("jobsFinished"), stats.jobDuration.sum().as("jobDuration"),
                             stats.flowFilesStarted.sum().as("flowFilesStarted"), stats.flowFilesFinished.sum().as("flowFilesFinished"), stats.totalCount.sum().as("totalCount"),
                             stats.maxEventTime.max().as("maxEventTime"), stats.minEventTime.min().as("minEventTime"), stats.jobsFailed.sum().as("jobsFailed"),
                             stats.count().as("resultSetCount"))
        )
            .from(stats)
            .where(stats.feedName.eq(feedName)
                       .and(stats.minEventTime.goe(start)
                                .and(stats.maxEventTime.loe(end))))
            .groupBy(stats.feedName, stats.processorName)
            .orderBy(stats.processorName.asc());

        return (List<JpaNifiFeedProcessorStats>) query.fetch();
    }

    public List<? extends JpaNifiFeedProcessorStats> findForFeedStatisticsGroupedByTime(String feedName, DateTime start, DateTime end) {
        QJpaNifiFeedProcessorStats stats = QJpaNifiFeedProcessorStats.jpaNifiFeedProcessorStats;
        JPAQuery
            query = factory.select(
            Projections.bean(JpaNifiFeedProcessorStats.class,
                             stats.feedName,
                             stats.bytesIn.sum().as("bytesIn"), stats.bytesOut.sum().as("bytesOut"), stats.duration.sum().as("duration"),
                             stats.jobsStarted.sum().as("jobsStarted"), stats.jobsFinished.sum().as("jobsFinished"), stats.jobDuration.sum().as("jobDuration"),
                             stats.flowFilesStarted.sum().as("flowFilesStarted"), stats.flowFilesFinished.sum().as("flowFilesFinished"),
                             stats.maxEventTime,
                             //stats.maxEventTime,
                             stats.jobsFailed.sum().as("jobsFailed"), stats.totalCount.sum().as("totalCount"),
                             stats.count().as("resultSetCount"))
        )
            .from(stats)
            .where(stats.feedName.eq(feedName)
                       .and(stats.minEventTime.goe(start)
                                .and(stats.maxEventTime.loe(end))))
            .groupBy(stats.feedName, stats.maxEventTime)
            .orderBy(stats.maxEventTime.asc());

        return (List<JpaNifiFeedProcessorStats>) query.fetch();
    }

    public Long findLastProcessedEventId() {
        return findLastProcessedEventId(null);
    }

    public Long findLastProcessedEventId(String clusterNodeId) {
        Long eventId = null;
        ItemLastModified lastModified = itemLastModifiedProvider.findByKey(getLastModifiedKey(clusterNodeId));
        if(lastModified != null){
            eventId = Long.parseLong(lastModified.getValue());
        }
        if(eventId == null){
           eventId = nifiEventProvider.findLastProcessedEventId(clusterNodeId);
        }
        //resort back to the max event id in case the other tables are not populated yet.  backwards comparability for 0.7.x versions
        if(eventId == null) {
            eventId = findMaxEventId(clusterNodeId);
        }
        return eventId;
    }

    public Long findMaxEventId(String clusterNodeId) {
        Long eventId = -1L;
        if (StringUtils.isNotBlank(clusterNodeId)) {
            eventId = statisticsRepository.findMaxEventId(clusterNodeId);
            if (eventId == null) {
                eventId = nifiEventRepository.findMaxEventId(clusterNodeId);
            }
        } else {
            eventId = findMaxEventId();
        }
        return eventId;
    }

    public Long findMaxEventId() {
        Long eventId = statisticsRepository.findMaxEventId();
        if (eventId == null) {
            eventId = nifiEventRepository.findMaxEventId();
        }
        return eventId;
    }

    @Override
    public Long resetLastProcessedEventId(String clusterNodeId) {
        ItemLastModified lastModified =  itemLastModifiedProvider.update(getLastModifiedKey(clusterNodeId),"0");
        return lastModified != null ? Long.parseLong(lastModified.getValue()) : null;
    }
}
