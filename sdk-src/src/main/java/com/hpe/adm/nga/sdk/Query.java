package com.hpe.adm.nga.sdk;

/**
 *    Copyright 2017 Hewlett-Packard Development Company, L.P.
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * Created by leufl on 10/10/2016.
 */
public class Query {

    private String queryString = "";

    /**
     * Negates the given query string
     * @param queryString - input query string
     * @return resulting string after negation
     */
    private static String negate(String queryString) {
        return "!" + queryString;
    }

    /**
     * QueryBuilder Statement
     * @param fieldName - field name
     * @param method - comparison function to use
     * @param fieldValue - value to compare with
     */
    public static QueryBuilder statement(String fieldName, QueryMethod method, Object fieldValue) {
        return new QueryBuilder(method.getAction().apply(fieldName, fieldValue));
    }

    /**
     * QueryBuilder not
     * @param fieldName - field name
     * @param method - comparison function to use
     * @param fieldValue - value to compare with
     */
    public static QueryBuilder not(String fieldName, QueryMethod method, Object fieldValue) {
        return new QueryBuilder(negate(method.getAction().apply(fieldName, fieldValue)));
    }

    /**
     * Constructor
     * @param builder - the query builder to use for building the query
     */
    private Query(QueryBuilder builder) {
        queryString = builder.queryString;
    }

    /**
     * Accessor method of query string
     * @return query string
     */
    public String getQueryString() {
        return queryString;
    }

    public static class QueryBuilder {

        private String queryString = "";

        private QueryBuilder(String queryString) {
            this.queryString = queryString;
        }

        /**
         * Accessor method for the query builder string.
         * @return query builder's string
         */
        private String getQueryString() {
            return queryString;
        }

        /**
         * Builds a query from the current builder
         * @return builded query
         */
        public Query build() {
            return new Query(this);
        }



        /**
         * Generates a builder by applying the logical "and" operator between the current builder and the resulting operation of the input values.
         * @param fieldName - field name
         * @param method - comparison function to use
         * @param fieldValue - value to compare with
         * @return resulting builder
         */
        public QueryBuilder and(String fieldName, QueryMethod method, Object fieldValue) {
            String rightQueryString = method.getAction().apply(fieldName, fieldValue);
            queryString += ";" + rightQueryString;
            return this;
        }

        /**
         * Generates a builder by applying the logical "and" operator between the current builder and the resulting operation of the input values after negation.
         * @param fieldName - field name
         * @param method - comparison function to use
         * @param fieldValue - value to compare with
         * @return resulting builder
         */
        public QueryBuilder andNot(String fieldName, QueryMethod method, Object fieldValue) {
            queryString += ";" + negate(method.getAction().apply(fieldName, fieldValue));
            return this;
        }

        /**
         * Generates a builder by applying the logical "and" operator between the current builder and the input builder.
         * @param qb - query builder
         * @return resulting builder
         */
        public QueryBuilder and(QueryBuilder qb) {
            queryString += ";" + qb.getQueryString();
            return this;
        }

        /**
         * Generates a builder by applying the logical "and" operator between the current builder and the input builder after negation.
         * @param qb - query builder
         * @return resulting builder
         */
        public QueryBuilder andNot(QueryBuilder qb) {
            queryString += ";" + negate(qb.getQueryString());
            return this;
        }

        /**
         * Generates a builder by applying the logical "or" operator between the current builder and the resulting operation of the input values.
         * @param fieldName - field name
         * @param method - comparison function to use
         * @param fieldValue - value to compare with
         * @return resulting builder
         */
        public QueryBuilder or(String fieldName, QueryMethod method, Object fieldValue) {
            String rightQueryString = method.getAction().apply(fieldName, fieldValue);
            queryString += "||" + rightQueryString;
            return this;
        }

        /**
         * Generates a builder by applying the logical "or" operator between the current builder and the resulting operation of the input values after negation.
         * @param fieldName - field name
         * @param method - comparison function to use
         * @param fieldValue - value to compare with
         * @return resulting builder
         */
        public QueryBuilder orNot(String fieldName, QueryMethod method, Object fieldValue) {
            queryString += "||" + negate(method.getAction().apply(fieldName, fieldValue));
            return this;
        }

        /**
         * Generates a builder by applying the logical "or" operator between the current builder and the input builder.
         * @param qb - query builder
         * @return resulting builder
         */
        public QueryBuilder or(QueryBuilder qb) {
            queryString += "||" + qb.getQueryString();
            return this;
        }

        /**
         * Generates a builder by applying the logical "or" operator between the current builder and the input builder after negation.
         * @param qb - query builder
         * @return resulting builder
         */
        public QueryBuilder orNot(QueryBuilder qb) {
            queryString += "||" + negate(qb.getQueryString());
            return this;
        }
    }


}
