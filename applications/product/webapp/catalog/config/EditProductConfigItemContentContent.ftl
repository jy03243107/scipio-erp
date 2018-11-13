<#--
This file is subject to the terms and conditions defined in
 file 'LICENSE', which is part of this source code package.
-->

<@menu type="button">
  <@menuitem type="link" href=makeOfbizUrl("EditProductConfigItemContent?configItemId=${configItemId}") text="${rawLabel('ProductProduct')} ${rawLabel('ProductConfigItem')} ${rawLabel('ProductContent')} ${rawLabel('CommonList')}" class="+${styles.action_nav!}" />
<#if contentId?has_content>
  <@menuitem type="link" href=makeOfbizInterWebappUrl("/content/control/EditContent?contentId=${contentId}") target='_blank' text="${rawLabel('ProductContent')} ${rawLabel('CommonPage')}" class="+${styles.action_nav!}" />
</#if>
</@menu>
<br />
<#if configItemId?has_content && productContent?has_content>
    ${updateProductContentWrapper.renderFormString(context)}
</#if>