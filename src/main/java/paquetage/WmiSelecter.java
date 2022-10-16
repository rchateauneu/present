package paquetage;

import COM.Wbemcli;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class WmiSelecter extends BaseSelecter {
    static WmiProvider wmiProvider = new WmiProvider();

    final static private Logger logger = Logger.getLogger(WmiSelecter.class);

    public boolean MatchProvider(QueryData queryData) {
        return true;
    }

    /** This runs a WQL query whose parameters are in a QueryData.
     * TODO: For some classes which do not change, when a query was run and does not return too many elements,
     * TODO: store the result in a cache. It needs to know if results might change between two runs,
     * TODO: between two machine startup (could be stored in a file cache) etc...
     * @param queryData
     * @return A list of rows containing the values of the variables as taken from the query results.
     * @throws Exception
     */
    public Solution EffectiveSelect(QueryData queryData) throws Exception {
        if (queryData.isMainVariableAvailable) {
            throw new Exception("Main variable should not be available in a WQL query.");
        }

        String wqlQuery = queryData.BuildWqlQuery();
        logger.debug("wqlQuery=" + wqlQuery);

        Solution cachedResultRows = queryData.GetCachedQueryResults(wqlQuery);
        if(cachedResultRows != null) {
            logger.debug("CACHE HIT - CACHE HIT - CACHE HIT - CACHE HIT - CACHE HIT- CACHE HIT");
            return cachedResultRows;
        }

        Solution resultRows = new Solution();

        // The results are batched in a big number, so it is faster.
        int countRows = 1000;

        Wbemcli.IWbemServices wbemService = wmiProvider.GetWbemService(queryData.namespace);

        /**
         * Not always necessary to add __PATH in the selected fields. Possibly consider WBEM_FLAG_ENSURE_LOCATABLE.
         * WBEM_FLAG_RETURN_IMMEDIATELY might be faster than WBEM_FLAG_RETURN_WBEM_COMPLETE
         *
         * When selecting a single column "MyColumn", the returned effective columns are:
         *     __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH, __PROPERTY_COUNT,
         *     __DERIVATION, __SERVER, __NAMESPACE, __PATH, MyColumn
         */
        Wbemcli.IEnumWbemClassObject enumerator = wbemService.ExecQuery(
                "WQL", wqlQuery,
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY,
                null);
        logger.debug("wqlQuery execution finished");
        int totalRows = 0;
        try {
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            while (true) {
                logger.debug("Next start totalRows=" + totalRows + " by " + countRows);
                Wbemcli.IWbemClassObject[] wqlResults = enumerator.Next(Wbemcli.WBEM_INFINITE, countRows);
                int queryLength = wqlResults.length;
                totalRows += queryLength;
                for (int indexRow = 0; indexRow < queryLength; ++indexRow) {
                    Wbemcli.IWbemClassObject wqlResult = wqlResults[indexRow];
                    Solution.Row oneRow = new Solution.Row();
                    // The path is always returned if the key is selected.
                    // This path should never be recalculated to ensure consistency with WMI.
                    // All values are NULL except, typically:
                    //     __CLASS=Win32_Process
                    //     __RELPATH=Win32_Process.Handle="4"

                    // This lambda extracts the value of a single column.
                    BiConsumer<String, String> storeValue = (String lambda_column, String lambda_variable) -> {
                        WinNT.HRESULT hr = wqlResult.Get(lambda_column, 0, pVal, pType, null);
                        COMUtils.checkRC(hr);
                        ValueTypePair rowValueType = WmiProvider.VariantToValueTypePair(lambda_column, pType, pVal);
                        oneRow.PutValueType(lambda_variable, rowValueType);
                        OleAuto.INSTANCE.VariantClear(pVal);
                    };

                    queryData.queryColumns.forEach(storeValue);
                    // Also get the path of each returned object.
                    storeValue.accept("__PATH", queryData.mainVariable);
                    wqlResult.Release();
                    resultRows.add(oneRow);
                }
                if (queryLength < countRows) {
                    break;
                }
            }
        } finally {
            enumerator.Release();
        }
        logger.debug("Leaving. Rows=" + resultRows.size() + "/" + totalRows);
        queryData.StoreCachedQueryResults(wqlQuery, resultRows);
        return resultRows;
    }
}
