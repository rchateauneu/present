package paquetage;

import COM.Wbemcli;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.function.BiConsumer;

public class WmiSelecter extends BaseSelecter {
    // TODO: Try a singleton.
    WmiProvider wmiProvider = new WmiProvider();

    final static private Logger logger = Logger.getLogger(WmiSelecter.class);

    public boolean MatchProvider(QueryData queryData) {
        return true;
    }

    /** This runs a WQL query whose parameters are in a QueryData.
     * TODO: For some classes which do not change, when a query was run and does not return too many elements,
     * TODO: store the result in a cache. It needs to know if results might change between two runs,
     * TODO: between two machine startup (could be stored in a file cache) etc...
     * TODO: Also, results of a query could be stored, and its cache could be used for another query,
     * TODO: similar but with extra "where" parameters.
     * @param queryData
     * @return A list of rows containing the values of the variables as taken from the query results.
     * @throws Exception
     */
    public ArrayList<GenericProvider.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericProvider.Row> resultRows = new ArrayList<>();
        String wqlQuery = queryData.BuildWqlQuery();
        // Temporary debugging purpose.
        logger.debug("wqlQuery=" + wqlQuery);

        if (queryData.isMainVariableAvailable) {
            throw new Exception("Main variable should not be available in a WQL query.");
        }

        // The results are batched in a big number, so it is faster.
        int countRows = 100;

        // Not always necessary to add __PATH in the selected fields. Possibly consider WBEM_FLAG_ENSURE_LOCATABLE.
        Wbemcli.IEnumWbemClassObject enumerator = wmiProvider.svc.ExecQuery("WQL", wqlQuery,
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
        logger.debug("wqlQuery finished");
        try {
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = null;
            while (true) {
                /**
                 * When selecting a single column "MyColumn", the returned effective columns are:
                 *     __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH, __PROPERTY_COUNT,
                 *     __DERIVATION, __SERVER, __NAMESPACE, __PATH, MyColumn
                 */
                Wbemcli.IWbemClassObject[] wqlResults = enumerator.Next(0, countRows);
                if (wqlResults.length == 0) {
                    break;
                }
                for (int indexRow = 0; indexRow < wqlResults.length; ++indexRow) {
                    Wbemcli.IWbemClassObject wqlResult = wqlResults[indexRow];
                    GenericProvider.Row oneRow = new GenericProvider.Row();
                    // The path is always returned if the key is selected.
                    // This path should never be recalculated to ensure consistency with WMI.
                    // All values are NULL except, typically:
                    //     __CLASS=Win32_Process
                    //     __RELPATH=Win32_Process.Handle="4"
                    if (false) {
                        String[] names = wqlResult.GetNames(null, 0, null);
                        System.out.println("names=" + String.join("+", names));

                        for (String col : names) {
                            COMUtils.checkRC(wqlResult.Get(col, 0, pVal, pType, plFlavor));
                            if (pVal.getValue() == null)
                                System.out.println(col + "=" + "NULL");
                            else
                                System.out.println(col + "=" + pVal.getValue().toString());
                            OleAuto.INSTANCE.VariantClear(pVal);
                        }
                    }

                    // This lambda extracts the value of a single column.
                    BiConsumer<String, String> storeValue = (String lambda_column, String lambda_variable) -> {
                        WinNT.HRESULT hr = wqlResult.Get(lambda_column, 0, pVal, pType, plFlavor);
                        COMUtils.checkRC(hr);

                        GenericProvider.Row.ValueTypePair rowValueType = WmiProvider.VariantToValueTypePair(lambda_column, lambda_variable, pType, pVal);
                        oneRow.PutValueType(lambda_variable, rowValueType);

                        OleAuto.INSTANCE.VariantClear(pVal);
                    };

                    queryData.queryColumns.forEach(storeValue);
                    // Also get the path of each returned object.
                    storeValue.accept("__PATH", queryData.mainVariable);
                    wqlResult.Release();
                    resultRows.add(oneRow);
                }
            }
        } finally {
            enumerator.Release();
        }
        logger.debug("Leaving. Rows=" + resultRows.size());
        return resultRows;
    }
}
