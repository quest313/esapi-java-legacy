package org.owasp.esapi.filters.waf.rules;

import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.owasp.esapi.filters.waf.actions.Action;
import org.owasp.esapi.filters.waf.actions.DefaultAction;
import org.owasp.esapi.filters.waf.actions.DoNothingAction;
import org.owasp.esapi.filters.waf.configuration.AppGuardianConfiguration;
import org.owasp.esapi.filters.waf.internal.InterceptingHTTPServletRequest;
import org.owasp.esapi.filters.waf.internal.InterceptingHTTPServletResponse;

public class SimpleVirtualPatchRule extends Rule {

	private static final String REQUEST_PARAMETERS = "request.parameters.";
	private static final String REQUEST_HEADERS = "request.headers.";

	private Pattern path;
	private String variable;
	private Pattern valid;
	private String message;

	public SimpleVirtualPatchRule(String id, Pattern path, String variable, Pattern valid, String message) {
		setId(id);
		this.path = path;
		this.variable = variable;
		this.valid = valid;
		this.message = message;
	}

	public Action check(HttpServletRequest req,
			InterceptingHTTPServletResponse response) {

		InterceptingHTTPServletRequest request = (InterceptingHTTPServletRequest)req;

		if ( ! path.matcher(request.getRequestURI()).matches() ) {

			return new DoNothingAction();

		} else {

			/*
			 * Decide which parameters/headers to act on.
			 */
			String target = null;
			Enumeration en = null;
			boolean parameter = true;

			if ( variable.startsWith(REQUEST_PARAMETERS)) {

				target = variable.substring(REQUEST_PARAMETERS.length());
				en = request.getParameterNames();

			} else if ( variable.startsWith(REQUEST_HEADERS) ) {

				parameter = false;
				target = variable.substring(REQUEST_HEADERS.length());
				en = request.getHeaderNames();

			} else {
				log(request, "Patch failed (improperly configured variable '" + variable + "')");
				return new DefaultAction();
			}

			/*
			 * If it contains a regex character, it's a regex. Loop through elements and grab any matches.
			 */
			if ( target.contains("*") || target.contains("?") ) {

				target = target.replaceAll("*", ".*");
				Pattern p = Pattern.compile(target);
				while (en.hasMoreElements() ) {
					String s = (String)en.nextElement();
					String value = null;
					if ( p.matcher(target).matches() ) {
						if ( parameter ) {
							value = request.getDictionaryParameter(s);
						} else {
							value = request.getHeader(s);
						}
						if ( ! valid.matcher(value).matches() ) {
							log(request, "Virtual patch tripped on variable '" + variable + "' (specifically '" + s + "'). User input was '" + value + "' and legal pattern was '" + valid.pattern() + "'");
							return new DefaultAction();
						}
					}
				}

			} else {

				if ( parameter ) {
					String value = request.getDictionaryParameter(target);
					if ( valid.matcher(value).matches() ) {
						return new DoNothingAction();
					} else {
						log(request, "Virtual patch tripped on parameter '" + target + "'. User input was '" + value + "' and legal pattern was '" + valid.pattern() + "'");
						return new DefaultAction();
					}
				} else {
					String value = request.getHeader(target);
					if ( valid.matcher(value).matches() ) {
						return new DoNothingAction();
					} else {
						log(request, "Virtual patch tripped on header '" + target + "'. User input was '" + value + "' and legal pattern was '" + valid.pattern() + "'");
						return new DefaultAction();
					}
				}
			}

		}

		log(request, "Virtual patch improperly configured (fell through)");

		return new DefaultAction();
	}

}