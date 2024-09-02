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
package io.trino.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.slice.Slices;
import io.trino.sql.ir.Call;
import io.trino.sql.ir.Cast;
import io.trino.sql.ir.Comparison;
import io.trino.sql.ir.Constant;
import io.trino.sql.ir.Reference;
import io.trino.sql.ir.Row;
import io.trino.sql.planner.assertions.PlanMatchPattern;
import io.trino.sql.planner.iterative.rule.test.BaseRuleTest;
import io.trino.sql.planner.plan.JoinNode.EquiJoinClause;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RowType.field;
import static io.trino.spi.type.RowType.rowType;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.sql.ir.Comparison.Operator.GREATER_THAN;
import static io.trino.sql.planner.assertions.PlanMatchPattern.project;
import static io.trino.sql.planner.assertions.PlanMatchPattern.strictProject;
import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.planner.plan.JoinType.FULL;
import static io.trino.sql.planner.plan.JoinType.INNER;
import static io.trino.sql.planner.plan.JoinType.LEFT;
import static io.trino.sql.planner.plan.JoinType.RIGHT;

public class TestReplaceJoinOverConstantWithProject
        extends BaseRuleTest
{
    @Test
    public void testDoesNotFireOnJoinWithEmptySource()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(1, p.symbol("a")),
                                p.values(0, p.symbol("b"))))
                .doesNotFire();

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(0, p.symbol("a")),
                                p.values(1, p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void testDoesNotFireOnJoinWithCondition()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(1, p.symbol("a")),
                                p.values(5, p.symbol("b")),
                                new EquiJoinClause(p.symbol("a"), p.symbol("b"))))
                .doesNotFire();

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(1, p.symbol("a")),
                                p.values(5, p.symbol("b")),
                                new Comparison(GREATER_THAN, new Reference(BIGINT, "a"), new Reference(BIGINT, "b"))))
                .doesNotFire();
    }

    @Test
    public void testDoesNotFireOnValuesWithMultipleRows()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(5, p.symbol("a")),
                                p.values(5, p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void testDoesNotFireOnValuesWithNoOutputs()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(1),
                                p.values(5, p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void testDoesNotFireOnValuesWithNonRowExpression()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a")), ImmutableList.of(new Cast(new Row(ImmutableList.of(new Constant(VARCHAR, Slices.utf8Slice("true")))), rowType(field("b", BOOLEAN))))),
                                p.values(5, p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void testDoesNotFireOnOuterJoinWhenSourcePossiblyEmpty()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                LEFT,
                                p.values(1, p.symbol("a")),
                                p.filter(
                                        new Comparison(GREATER_THAN, new Reference(INTEGER, "b"), new Constant(INTEGER, 5L)),
                                        p.values(10, p.symbol("b")))))
                .doesNotFire();

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                RIGHT,
                                p.filter(
                                        new Comparison(GREATER_THAN, new Reference(INTEGER, "a"), new Constant(INTEGER, 5L)),
                                        p.values(10, p.symbol("a"))),
                                p.values(1, p.symbol("b"))))
                .doesNotFire();

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                FULL,
                                p.values(1, p.symbol("a")),
                                p.filter(
                                        new Comparison(GREATER_THAN, new Reference(INTEGER, "b"), new Constant(INTEGER, 5L)),
                                        p.values(10, p.symbol("b")))))
                .doesNotFire();

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                FULL,
                                p.filter(
                                        new Comparison(GREATER_THAN, new Reference(INTEGER, "a"), new Constant(INTEGER, 5L)),
                                        p.values(10, p.symbol("a"))),
                                p.values(1, p.symbol("b"))))
                .doesNotFire();
    }

    @Test
    public void testReplaceInnerJoinWithProject()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x")))))),
                                p.values(5, p.symbol("c"))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.values(5, p.symbol("c")),
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x"))))))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));
    }

    @Test
    public void testReplaceLeftJoinWithProject()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                LEFT,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x")))))),
                                p.values(5, p.symbol("c"))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                LEFT,
                                p.values(5, p.symbol("c")),
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x"))))))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));
    }

    @Test
    public void testReplaceRightJoinWithProject()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                RIGHT,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x")))))),
                                p.values(5, p.symbol("c"))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                RIGHT,
                                p.values(5, p.symbol("c")),
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x"))))))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));
    }

    @Test
    public void testReplaceFullJoinWithProject()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                FULL,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x")))))),
                                p.values(5, p.symbol("c"))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                FULL,
                                p.values(5, p.symbol("c")),
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x"))))))))
                .matches(
                        project(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));
    }

    @Test
    public void testRemoveOutputDuplicates()
    {
        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR)), ImmutableList.of(new Row(ImmutableList.of(new Constant(INTEGER, 1L), new Constant(VARCHAR, Slices.utf8Slice("x")))))),
                                p.values(5, p.symbol("c")),
                                ImmutableList.of(),
                                ImmutableList.of(p.symbol("a", INTEGER), p.symbol("b", VARCHAR), p.symbol("a", INTEGER), p.symbol("b", VARCHAR)),
                                ImmutableList.of(p.symbol("c"), p.symbol("c")),
                                Optional.empty()))
                .matches(
                        strictProject(
                                ImmutableMap.of(
                                        "a", PlanMatchPattern.expression(new Constant(INTEGER, 1L)),
                                        "b", PlanMatchPattern.expression(new Constant(VARCHAR, Slices.utf8Slice("x"))),
                                        "c", PlanMatchPattern.expression(new Reference(BIGINT, "c"))),
                                values("c")));
    }

    @Test
    public void testNonDeterministicValues()
    {
        Call randomFunction = new Call(
                tester().getMetadata().resolveBuiltinFunction("random", ImmutableList.of()),
                ImmutableList.of());

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("rand")), ImmutableList.of(new Row(ImmutableList.of(randomFunction)))),
                                p.values(5, p.symbol("b"))))
                .doesNotFire();

        Call uuidFunction = new Call(
                tester().getMetadata().resolveBuiltinFunction("uuid", ImmutableList.of()),
                ImmutableList.of());

        tester().assertThat(new ReplaceJoinOverConstantWithProject())
                .on(p ->
                        p.join(
                                INNER,
                                p.valuesOfExpressions(ImmutableList.of(p.symbol("uuid")), ImmutableList.of(new Row(ImmutableList.of(uuidFunction)))),
                                p.values(5, p.symbol("b"))))
                .doesNotFire();
    }
}
