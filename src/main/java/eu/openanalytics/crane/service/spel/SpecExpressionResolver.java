/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.crane.service.spel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Note: inspired by org.springframework.context.expression.StandardBeanExpressionResolver
 */
@Component
public class SpecExpressionResolver {

    private final ApplicationContext appContext;
    private final ExpressionParser expressionParser;
    private final Cache<SpecExpressionContext, StandardEvaluationContext> evaluationCache = Caffeine.newBuilder()
        .scheduler(Scheduler.systemScheduler())
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build();

    private final ParserContext beanExpressionParserContext = new ParserContext() {
        @Override
        public boolean isTemplate() {
            return true;
        }

        @Override
        public String getExpressionPrefix() {
            return StandardBeanExpressionResolver.DEFAULT_EXPRESSION_PREFIX;
        }

        @Override
        public String getExpressionSuffix() {
            return StandardBeanExpressionResolver.DEFAULT_EXPRESSION_SUFFIX;
        }
    };

    public SpecExpressionResolver(ApplicationContext appContext) {
        this.appContext = appContext;
        this.expressionParser = new SpelExpressionParser();
    }

    public Object evaluate(String expression, SpecExpressionContext context) {
        if (expression == null) return null;
        if (expression.isEmpty()) return "";

        Expression expr = this.expressionParser.parseExpression(expression, this.beanExpressionParserContext);

        ConfigurableBeanFactory beanFactory = ((ConfigurableApplicationContext) appContext).getBeanFactory();

        StandardEvaluationContext sec = evaluationCache.get(context, k -> {
            StandardEvaluationContext result = new StandardEvaluationContext();
            result.setRootObject(context);
            result.addPropertyAccessor(new BeanExpressionContextAccessor());
            result.addPropertyAccessor(new BeanFactoryAccessor());
            result.addPropertyAccessor(new MapAccessor());
            result.addPropertyAccessor(new EnvironmentAccessor());
            result.setBeanResolver(new BeanFactoryResolver(appContext));
            result.setTypeLocator(new StandardTypeLocator(beanFactory.getBeanClassLoader()));
            ConversionService conversionService = beanFactory.getConversionService();
            if (conversionService != null) result.setTypeConverter(new StandardTypeConverter(conversionService));
            return result;
        });

        return expr.getValue(sec);
    }

    public String evaluateToString(String expression, SpecExpressionContext context) {
        return String.valueOf(evaluate(expression, context));
    }

    public Boolean evaluateToBoolean(String expression, SpecExpressionContext context) {
        return Boolean.valueOf(evaluateToString(expression, context));
    }
}
