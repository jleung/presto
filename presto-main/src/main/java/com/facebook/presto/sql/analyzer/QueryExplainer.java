/*
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
 */
package com.facebook.presto.sql.analyzer;

import com.facebook.presto.Session;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.planner.DistributedLogicalPlanner;
import com.facebook.presto.sql.planner.LogicalPlanner;
import com.facebook.presto.sql.planner.Plan;
import com.facebook.presto.sql.planner.PlanNodeIdAllocator;
import com.facebook.presto.sql.planner.PlanPrinter;
import com.facebook.presto.sql.planner.SubPlan;
import com.facebook.presto.sql.planner.optimizations.PlanOptimizer;
import com.facebook.presto.sql.tree.ExplainType;
import com.facebook.presto.sql.tree.Statement;
import com.google.common.base.Optional;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class QueryExplainer
{
    private final Session session;
    private final List<PlanOptimizer> planOptimizers;
    private final Metadata metadata;
    private final SqlParser sqlParser;
    private final boolean experimentalSyntaxEnabled;
    private final boolean distributedIndexJoinsEnabled;
    private final boolean distributedJoinsEnabled;

    public QueryExplainer(
            Session session,
            List<PlanOptimizer> planOptimizers,
            Metadata metadata,
            SqlParser sqlParser,
            boolean experimentalSyntaxEnabled,
            boolean distributedIndexJoinsEnabled,
            boolean distributedJoinsEnabled)
    {
        this.session = checkNotNull(session, "session is null");
        this.planOptimizers = checkNotNull(planOptimizers, "planOptimizers is null");
        this.metadata = checkNotNull(metadata, "metadata is null");
        this.sqlParser = checkNotNull(sqlParser, "sqlParser is null");
        this.experimentalSyntaxEnabled = experimentalSyntaxEnabled;
        this.distributedIndexJoinsEnabled = distributedIndexJoinsEnabled;
        this.distributedJoinsEnabled = distributedJoinsEnabled;
    }

    public String getPlan(Statement statement, ExplainType.Type planType)
    {
        switch (planType) {
            case LOGICAL:
                Plan plan = getLogicalPlan(statement);
                return PlanPrinter.textLogicalPlan(plan.getRoot(), plan.getTypes(), metadata);
            case DISTRIBUTED:
                SubPlan subPlan = getDistributedPlan(statement);
                return PlanPrinter.textDistributedPlan(subPlan, metadata);
        }
        throw new IllegalArgumentException("Unhandled plan type: " + planType);
    }

    public String getGraphvizPlan(Statement statement, ExplainType.Type planType)
    {
        switch (planType) {
            case LOGICAL:
                Plan plan = getLogicalPlan(statement);
                return PlanPrinter.graphvizLogicalPlan(plan.getRoot(), plan.getTypes());
            case DISTRIBUTED:
                SubPlan subPlan = getDistributedPlan(statement);
                return PlanPrinter.graphvizDistributedPlan(subPlan);
        }
        throw new IllegalArgumentException("Unhandled plan type: " + planType);
    }

    public String getJsonPlan(Statement statement)
    {
        Plan plan = getLogicalPlan(statement);
        return PlanPrinter.getJsonPlanSource(plan.getRoot(), metadata);
    }

    private Plan getLogicalPlan(Statement statement)
    {
        // analyze statement
        Analyzer analyzer = new Analyzer(session, metadata, sqlParser, Optional.of(this), experimentalSyntaxEnabled);

        Analysis analysis = analyzer.analyze(statement);
        PlanNodeIdAllocator idAllocator = new PlanNodeIdAllocator();

        // plan statement
        LogicalPlanner logicalPlanner = new LogicalPlanner(session, planOptimizers, idAllocator, metadata);
        return logicalPlanner.plan(analysis);
    }

    private SubPlan getDistributedPlan(Statement statement)
    {
        // analyze statement
        Analyzer analyzer = new Analyzer(session, metadata, sqlParser, Optional.of(this), experimentalSyntaxEnabled);

        Analysis analysis = analyzer.analyze(statement);
        PlanNodeIdAllocator idAllocator = new PlanNodeIdAllocator();

        // plan statement
        LogicalPlanner logicalPlanner = new LogicalPlanner(session, planOptimizers, idAllocator, metadata);
        Plan plan = logicalPlanner.plan(analysis);

        return new DistributedLogicalPlanner(session, metadata, idAllocator).createSubPlans(plan, false, distributedIndexJoinsEnabled, distributedJoinsEnabled);
    }
}
