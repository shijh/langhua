<#--
  Learning curve estimation page.
  Parameters: historyData, newUnitNumber, leastSquare, methodType
  Service results are placed in request attributes by the service event.
-->
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>Learning Curve Estimation</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 2em; color: #222; }
        h1 { font-size: 1.5em; }
        label { display: inline-block; width: 11em; }
        table { border-collapse: collapse; margin-top: .8em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        .error { color: #b00000; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
    </style>
</head>
<body>
<h1>Learning Curve Estimation</h1>

<form method="post" action="/learningcurve/control/estimate">
    <p>
        <label for="historyData">History data</label>
        <input type="text" id="historyData" name="historyData" size="45"
               value="${(parameters.historyData)!"2:200;3:285;5:450"}"/>
        <span class="hint">lot size:cost pairs, separated by ; or ,</span>
    </p>
    <p>
        <label for="newUnitNumber">New unit number</label>
        <input type="text" id="newUnitNumber" name="newUnitNumber" size="10"
               value="${(parameters.newUnitNumber)!"5"}"/>
        <span class="hint">number of units to estimate</span>
    </p>
    <p>
        <label for="leastSquare">Least square</label>
        <input type="text" id="leastSquare" name="leastSquare" size="6"
               value="${(parameters.leastSquare)!"0.95"}"/>
        <span class="hint">desired R&sup2; between 0 and 1</span>
    </p>
    <p>
        <label for="methodType">Method</label>
        <select id="methodType" name="methodType">
            <option value="1"<#if (parameters.methodType!"1") == "1"> selected="selected"</#if>>1 - Wright</option>
            <option value="2"<#if (parameters.methodType!"") == "2"> selected="selected"</#if>>2 - Crawford</option>
        </select>
    </p>
    <p><input type="submit" value="Estimate"/></p>
</form>

<#if requestAttributes["_ERROR_MESSAGE_"]?? && requestAttributes["_ERROR_MESSAGE_"]?has_content>
    <p class="error">${requestAttributes["_ERROR_MESSAGE_"]}</p>
</#if>

<#if requestAttributes.estimate??>
    <h2>Result</h2>
    <table>
        <tr><th>estimate (total cost of the new units)</th><td>${requestAttributes.estimate!}</td></tr>
        <tr><th>slope</th><td>${requestAttributes.slope!}</td></tr>
        <tr><th>intercept</th><td>${requestAttributes.intercept!}</td></tr>
        <tr><th>least square (actual)</th><td>${requestAttributes.leastSquareActual!}</td></tr>
        <tr><th>qualified rows</th><td>${requestAttributes.qualifiedRowCount!}</td></tr>
    </table>
</#if>
</body>
</html>
