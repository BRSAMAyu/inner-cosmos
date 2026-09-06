package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.entity.CommercialMetricEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommercialMetricEventMapper extends BaseMapper<CommercialMetricEvent> {

    /**
     * Minimal projection rows for one metric code and week range. The K1/K2/G-SAFE
     * contracts are computed over these rows in CommercialMetricQueryServiceImpl so the
     * same SQL stays portable across H2 (tests) and PostgreSQL (production).
     */
    @Select("""
            SELECT e.user_id AS userId, e.anchor_day AS anchorDay, e.anchor_week AS anchorWeek,
                   e.context_id AS contextId, e.dim_a AS dimA, e.dim_b AS dimB, e.props AS props
            FROM tb_commercial_metric_event e
            WHERE e.metric_code = #{code}
              AND e.anchor_week >= #{fromWeek} AND e.anchor_week <= #{toWeek}
              AND e.ingest_source = 'SERVER_CONFIRMED'
            """)
    List<MetricProjection> projectRange(@Param("code") String code,
                                        @Param("fromWeek") String fromWeek,
                                        @Param("toWeek") String toWeek);

    /** Projection carrier; MyBatis auto-maps by column alias to the setters. */
    class MetricProjection {
        private Long userId;
        private String anchorDay;
        private String anchorWeek;
        private String contextId;
        private String dimA;
        private String dimB;
        private String props;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getAnchorDay() {
            return anchorDay;
        }

        public void setAnchorDay(String anchorDay) {
            this.anchorDay = anchorDay;
        }

        public String getAnchorWeek() {
            return anchorWeek;
        }

        public void setAnchorWeek(String anchorWeek) {
            this.anchorWeek = anchorWeek;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }

        public String getDimA() {
            return dimA;
        }

        public void setDimA(String dimA) {
            this.dimA = dimA;
        }

        public String getDimB() {
            return dimB;
        }

        public void setDimB(String dimB) {
            this.dimB = dimB;
        }

        public String getProps() {
            return props;
        }

        public void setProps(String props) {
            this.props = props;
        }
    }
}
