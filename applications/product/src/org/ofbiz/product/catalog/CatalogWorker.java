/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.product.catalog;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.product.category.CategoryWorker;
import org.ofbiz.product.store.ProductStoreWorker;
import org.ofbiz.webapp.website.WebSiteWorker;

/**
 * CatalogWorker - Worker class for catalog related functionality
 */
public final class CatalogWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private CatalogWorker () {}


    /**
     * @deprecated - Use WebSiteWorker.getWebSiteId(ServletRequest) instead
     */
    @Deprecated
    public static String getWebSiteId(ServletRequest request) {
        return WebSiteWorker.getWebSiteId(request);
    }

    /**
     * @deprecated - Use WebSiteWorker.getWebSite(ServletRequest) instead
     */
    @Deprecated
    public static GenericValue getWebSite(ServletRequest request) {
        return WebSiteWorker.getWebSite(request);
    }

    public static List<String> getAllCatalogIds(ServletRequest request) {
        List<String> catalogIds = new LinkedList<String>();
        List<GenericValue> catalogs = null;
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        try {
            catalogs = EntityQuery.use(delegator).from("ProdCatalog").orderBy("catalogName").queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up all catalogs", module);
        }
        if (catalogs != null) {
            for (GenericValue c: catalogs) {
                catalogIds.add(c.getString("prodCatalogId"));
            }
        }
        return catalogIds;
    }

    public static List<GenericValue> getStoreCatalogs(ServletRequest request) {
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        return getStoreCatalogs(delegator, productStoreId);
    }

    public static List<GenericValue> getStoreCatalogs(Delegator delegator, String productStoreId) {
        try {
            return EntityQuery.use(delegator).from("ProductStoreCatalog").where("productStoreId", productStoreId).orderBy("sequenceNum", "prodCatalogId").cache(true).filterByDate().queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up store catalogs for store with id " + productStoreId, module);
        }
        return null;
    }

    public static List<GenericValue> getPartyCatalogs(ServletRequest request) {
        HttpSession session = ((HttpServletRequest) request).getSession();
        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        if (userLogin == null) userLogin = (GenericValue) session.getAttribute("autoUserLogin");
        if (userLogin == null) return null;
        String partyId = userLogin.getString("partyId");
        if (partyId == null) return null;
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        return getPartyCatalogs(delegator, partyId);
    }

    public static List<GenericValue> getPartyCatalogs(Delegator delegator, String partyId) {
        if (delegator == null || partyId == null) {
            return null;
        }

        try {
            return EntityQuery.use(delegator).from("ProdCatalogRole").where("partyId", partyId, "roleTypeId", "CUSTOMER").orderBy("sequenceNum", "prodCatalogId").cache(true).filterByDate().queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up ProdCatalog Roles for party with id " + partyId, module);
        }
        return null;
    }

    public static List<GenericValue> getProdCatalogCategories(ServletRequest request, String prodCatalogId, String prodCatalogCategoryTypeId) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        return getProdCatalogCategories(delegator, prodCatalogId, prodCatalogCategoryTypeId);
    }

    public static List<GenericValue> getProdCatalogCategories(Delegator delegator, String prodCatalogId, String prodCatalogCategoryTypeId) {
        try {
            List<GenericValue> prodCatalogCategories = EntityQuery.use(delegator).from("ProdCatalogCategory")
                    .where("prodCatalogId", prodCatalogId)
                    .orderBy("sequenceNum", "productCategoryId")
                    .cache(true)
                    .filterByDate()
                    .queryList();

            if (UtilValidate.isNotEmpty(prodCatalogCategoryTypeId) && prodCatalogCategories != null) {
                prodCatalogCategories = EntityUtil.filterByAnd(prodCatalogCategories,
                            UtilMisc.toMap("prodCatalogCategoryTypeId", prodCatalogCategoryTypeId));
            }
            return prodCatalogCategories;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up ProdCatalogCategories for prodCatalog with id " + prodCatalogId, module);
        }
        return null;
    }

    /**
     * Retrieves the current prodCatalogId.  First it will attempt to find it from a special
     * request parameter or session attribute named CURRENT_CATALOG_ID.  Failing that, it will
     * get the first catalog from the database as specified in getCatalogIdsAvailable().
     * If this behavior is undesired, give the user a selectable list of catalogs.
     * <p>
     * SCIPIO: 2017-08-15: now supports reading CURRENT_CATALOG_ID without storing back to session (save boolean).
     */
    public static String getCurrentCatalogId(ServletRequest request, boolean save, boolean saveTrail) {
        HttpSession session = ((HttpServletRequest) request).getSession();
        Map<String, Object> requestParameters = UtilHttp.getParameterMap((HttpServletRequest) request);
        String prodCatalogId = null;
        boolean fromSession = false;

        // first see if a new catalog was specified as a parameter
        prodCatalogId = (String) requestParameters.get("CURRENT_CATALOG_ID");
        // if no parameter, try from session
        if (prodCatalogId == null) {
            prodCatalogId = (String) session.getAttribute("CURRENT_CATALOG_ID");
            if (prodCatalogId != null) fromSession = true;
        }
        // get it from the database
        if (prodCatalogId == null) {
            List<String> catalogIds = getCatalogIdsAvailable(request);
            if (UtilValidate.isNotEmpty(catalogIds)) prodCatalogId = catalogIds.get(0);
        }

        if (save && !fromSession) {
            if (Debug.verboseOn()) Debug.logVerbose("[CatalogWorker.getCurrentCatalogId] Setting new catalog name: " + prodCatalogId, module);
            session.setAttribute("CURRENT_CATALOG_ID", prodCatalogId);
            if (saveTrail) {
                // SCIPIO: 2016-13-22: Do NOT override the trail if it was already set earlier in request,
                // otherwise may lose work done by servlets and filters
                //CategoryWorker.setTrail(request, new ArrayList<>());
                CategoryWorker.setTrailIfFirstInRequest(request, new ArrayList<>()); // SCIPIO: use ArrayList
            }
        }
        return prodCatalogId;
    }

    /**
     * Retrieves the current prodCatalogId.  First it will attempt to find it from a special
     * request parameter or session attribute named CURRENT_CATALOG_ID.  Failing that, it will
     * get the first catalog from the database as specified in getCatalogIdsAvailable().
     * If this behavior is undesired, give the user a selectable list of catalogs.
     * SCIPIO: This variant can optionally skip all saving to session.
     * Added 2017-08-15.
     */
    public static String getCurrentCatalogId(ServletRequest request, boolean save) {
        return getCurrentCatalogId(request, save, save);
    }

    /**
     * Retrieves the current prodCatalogId.  First it will attempt to find it from a special
     * request parameter or session attribute named CURRENT_CATALOG_ID.  Failing that, it will
     * get the first catalog from the database as specified in getCatalogIdsAvailable().
     * If this behavior is undesired, give the user a selectable list of catalogs.
     * <p>
     * SCIPIO: NOTE: 2017-08-15: this is the original; now delegates.
     */
    public static String getCurrentCatalogId(ServletRequest request) {
        return getCurrentCatalogId(request, true, true);
    }

    /**
     * Retrieves the current prodCatalogId.  First it will attempt to find it from a special
     * request parameter or session attribute named CURRENT_CATALOG_ID.  Failing that, it will
     * get the first catalog from the database as specified in getCatalogIdsAvailable().
     * If this behavior is undesired, give the user a selectable list of catalogs.
     * SCIPIO: This variant only reads and does not store the catalogId (or anything else)
     * back in session; intended for special purposes.
     * Added 2017-08-15.
     */
    public static String getCurrentCatalogIdReadOnly(ServletRequest request) {
        return getCurrentCatalogId(request, false, false);
    }

    public static List<String> getCatalogIdsAvailable(ServletRequest request) {
        List<GenericValue> partyCatalogs = getPartyCatalogs(request);
        List<GenericValue> storeCatalogs = getStoreCatalogs(request);
        return getCatalogIdsAvailable(partyCatalogs, storeCatalogs);
    }

    public static List<String> getCatalogIdsAvailable(Delegator delegator, String productStoreId, String partyId) {
        List<GenericValue> storeCatalogs = getStoreCatalogs(delegator, productStoreId);
        List<GenericValue> partyCatalogs = getPartyCatalogs(delegator, partyId);
        return getCatalogIdsAvailable(partyCatalogs, storeCatalogs);
    }

    public static List<String> getCatalogIdsAvailable(List<GenericValue> partyCatalogs, List<GenericValue> storeCatalogs) {
        List<String> categoryIds = new LinkedList<String>();
        List<GenericValue> allCatalogLinks = new LinkedList<GenericValue>();
        if (partyCatalogs != null) allCatalogLinks.addAll(partyCatalogs);
        if (storeCatalogs != null) allCatalogLinks.addAll(storeCatalogs);

        if (allCatalogLinks.size() > 0) {
            for (GenericValue catalogLink: allCatalogLinks) {
                categoryIds.add(catalogLink.getString("prodCatalogId"));
            }
        }
        return categoryIds;
    }

    public static String getCatalogName(ServletRequest request) {
        return getCatalogName(request, getCurrentCatalogId(request));
    }

    public static String getCatalogName(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        try {
            GenericValue prodCatalog = EntityQuery.use(delegator).from("ProdCatalog").where("prodCatalogId", prodCatalogId).cache().queryOne();

            if (prodCatalog != null) {
                return prodCatalog.getString("catalogName");
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up name for prodCatalog with id " + prodCatalogId, module);
        }

        return null;
    }

    public static String getContentPathPrefix(ServletRequest request) {
        GenericValue prodCatalog = getProdCatalog(request, getCurrentCatalogId(request));

        if (prodCatalog == null) return "";
        String contentPathPrefix = prodCatalog.getString("contentPathPrefix");

        return StringUtil.cleanUpPathPrefix(contentPathPrefix);
    }

    public static String getTemplatePathPrefix(ServletRequest request) {
        GenericValue prodCatalog = getProdCatalog(request, getCurrentCatalogId(request));

        if (prodCatalog == null) return "";
        String templatePathPrefix = prodCatalog.getString("templatePathPrefix");

        return StringUtil.cleanUpPathPrefix(templatePathPrefix);
    }

    public static GenericValue getProdCatalog(ServletRequest request) {
        return getProdCatalog(request, getCurrentCatalogId(request));
    }

    public static GenericValue getProdCatalog(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        try {
            return EntityQuery.use(delegator).from("ProdCatalog").where("prodCatalogId", prodCatalogId).cache().queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up name for prodCatalog with id " + prodCatalogId, module);
            return null;
        }
    }

    public static String getCatalogTopCategoryId(ServletRequest request) {
        return getCatalogTopCategoryId(request, getCurrentCatalogId(request));
    }

    public static String getCatalogTopCategoryId(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, "PCCT_BROWSE_ROOT");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    /**
     * SCIPIO: new overload that works with delegator instead of request.
     * Added 2017-11-09.
     */
    public static String getCatalogTopCategoryId(Delegator delegator, String prodCatalogId) {
        if (prodCatalogId == null || prodCatalogId.length() <= 0) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(delegator, prodCatalogId, "PCCT_BROWSE_ROOT");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static String getCatalogSearchCategoryId(ServletRequest request) {
        return getCatalogSearchCategoryId(request, getCurrentCatalogId(request));
    }

    public static String getCatalogSearchCategoryId(ServletRequest request, String prodCatalogId) {
        return getCatalogSearchCategoryId((Delegator) request.getAttribute("delegator"), prodCatalogId);
    }
    public static String getCatalogSearchCategoryId(Delegator delegator, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(delegator, prodCatalogId, "PCCT_SEARCH");
        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);
            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static String getCatalogViewAllowCategoryId(Delegator delegator, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(delegator, prodCatalogId, "PCCT_VIEW_ALLW");
        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);
            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static String getCatalogPurchaseAllowCategoryId(Delegator delegator, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(delegator, prodCatalogId, "PCCT_PURCH_ALLW");
        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);
            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static String getCatalogPromotionsCategoryId(ServletRequest request) {
        return getCatalogPromotionsCategoryId(request, getCurrentCatalogId(request));
    }

    public static String getCatalogPromotionsCategoryId(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, "PCCT_PROMOTIONS");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static boolean getCatalogQuickaddUse(ServletRequest request) {
        return getCatalogQuickaddUse(request, getCurrentCatalogId(request));
    }

    public static boolean getCatalogQuickaddUse(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return false;
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        try {
            GenericValue prodCatalog = EntityQuery.use(delegator).from("ProdCatalog").where("prodCatalogId", prodCatalogId).cache().queryOne();

            if (prodCatalog != null) {
                return "Y".equals(prodCatalog.getString("useQuickAdd"));
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error looking up name for prodCatalog with id " + prodCatalogId, module);
        }
        return false;
    }

    public static String getCatalogQuickaddCategoryPrimary(ServletRequest request) {
        return getCatalogQuickaddCategoryPrimary(request, getCurrentCatalogId(request));
    }

    public static String getCatalogQuickaddCategoryPrimary(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, "PCCT_QUICK_ADD");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    public static Collection<String> getCatalogQuickaddCategories(ServletRequest request) {
        return getCatalogQuickaddCategories(request, getCurrentCatalogId(request));
    }

    public static Collection<String> getCatalogQuickaddCategories(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        Collection<String> categoryIds = new LinkedList<String>();

        Collection<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, "PCCT_QUICK_ADD");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            for (GenericValue prodCatalogCategory: prodCatalogCategories) {
                categoryIds.add(prodCatalogCategory.getString("productCategoryId"));
            }
        }

        return categoryIds;
    }

    public static String getCatalogTopEbayCategoryId(ServletRequest request, String prodCatalogId) {
        if (UtilValidate.isEmpty(prodCatalogId)) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, "PCCT_EBAY_ROOT");

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    /**
     * SCIPIO: Returns the first root best-sell category for the current catalog.
     */
    public static String getCatalogBestSellCategoryId(ServletRequest request) {
        return getCatalogPromotionsCategoryId(request, getCurrentCatalogId(request));
    }

    /**
     * SCIPIO: Returns the first root best-sell category for the current catalog.
     */
    public static String getCatalogBestSellCategoryId(ServletRequest request, String prodCatalogId) {
        return getCatalogFirstCategoryId(request, "PCCT_BEST_SELL", prodCatalogId);
    }


    /**
     * SCIPIO: Returns the first root best-sell category for the current catalog.
     */
    public static String getCatalogFirstCategoryId(ServletRequest request, String prodCatalogCategoryTypeId) {
        return getCatalogFirstCategoryId(request, prodCatalogCategoryTypeId, getCurrentCatalogId(request));
    }

    /**
     * SCIPIO: Returns the first root category of the given prodCatalogCategoryTypeId type for the current catalog.
     */
    public static String getCatalogFirstCategoryId(ServletRequest request, String prodCatalogCategoryTypeId, String prodCatalogId) {
        if (prodCatalogId == null || prodCatalogId.length() <= 0) return null;

        List<GenericValue> prodCatalogCategories = getProdCatalogCategories(request, prodCatalogId, prodCatalogCategoryTypeId);

        if (UtilValidate.isNotEmpty(prodCatalogCategories)) {
            GenericValue prodCatalogCategory = EntityUtil.getFirst(prodCatalogCategories);

            return prodCatalogCategory.getString("productCategoryId");
        } else {
            return null;
        }
    }

    /**
     * SCIPIO: Imported from SolrCategoryUtil.
     * Added 2017-11-09.
     */
    public static List<GenericValue> getProductStoresFromCatalogIds(Delegator delegator, Collection<String> catalogIds, Timestamp moment, boolean useCache) {
        List<GenericValue> stores = new ArrayList<>();
        Set<String> storeIds = new HashSet<>();
        for(String catalogId : catalogIds) {
            try {
                List<GenericValue> productStoreCatalogs = EntityQuery.use(delegator).from("ProductStoreCatalog").where("prodCatalogId", catalogId)
                        .filterByDate(moment).cache(useCache).queryList();
                for(GenericValue productStoreCatalog : productStoreCatalogs) {
                    if (!storeIds.contains(productStoreCatalog.getString("productStoreId"))) {
                        stores.add(productStoreCatalog.getRelatedOne("ProductStore", useCache));
                        storeIds.add(productStoreCatalog.getString("productStoreId"));
                    }
                }
            } catch(Exception e) {
                Debug.logError(e, "Solr: Error looking up ProductStore for catalogId: " + catalogId, module);
            }
        }
        return stores;
    }

    public static List<GenericValue> getProductStoresFromCatalogIds(Delegator delegator, Collection<String> catalogIds, boolean useCache) {
        return getProductStoresFromCatalogIds(delegator, catalogIds, UtilDateTime.nowTimestamp(), useCache);
    }
}