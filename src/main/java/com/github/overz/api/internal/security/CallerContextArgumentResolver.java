package com.github.overz.api.internal.security;

import com.github.overz.shared.CallerContext;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Materializa o {@link CallerContext} como parâmetro de controller — a única forma
 * prevista de um controller obter identidade (SDD arquitetura §api).
 */
@Component
class CallerContextArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(final MethodParameter parameter) {
    return CallerContext.class.equals(parameter.getParameterType());
  }

  @Override
  public CallerContext resolveArgument(
    final MethodParameter parameter,
    final ModelAndViewContainer mavContainer,
    final NativeWebRequest webRequest,
    final WebDataBinderFactory binderFactory
  ) {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CallerContext context) {
      return context;
    }
    throw new AuthenticationCredentialsNotFoundException(
      "Nenhum CallerContext no contexto de segurança — rota autenticada sem token convertido");
  }

}
