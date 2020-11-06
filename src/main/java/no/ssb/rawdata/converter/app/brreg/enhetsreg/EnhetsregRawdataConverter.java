package no.ssb.rawdata.converter.app.brreg.enhetsreg;

import lombok.extern.slf4j.Slf4j;
import no.ssb.rawdata.api.RawdataMessage;
import no.ssb.rawdata.converter.core.convert.ConversionResult;
import no.ssb.rawdata.converter.core.convert.ConversionResult.ConversionResultBuilder;
import no.ssb.rawdata.converter.core.convert.RawdataConverter;
import no.ssb.rawdata.converter.core.convert.ValueInterceptorChain;
import no.ssb.rawdata.converter.core.exception.RawdataConverterException;
import no.ssb.rawdata.converter.core.schema.AggregateSchemaBuilder;
import no.ssb.rawdata.converter.core.schema.DcManifestSchemaAdapter;
import no.ssb.rawdata.converter.util.RawdataMessageAdapter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecordBuilder;
import no.ssb.avro.convert.json.ToGenericRecord;

import java.util.Collection;

import static no.ssb.rawdata.converter.util.AvroSchemaUtil.readAvroSchema;
import static no.ssb.rawdata.converter.util.RawdataMessageAdapter.posAndIdOf;

@Slf4j
public class EnhetsregRawdataConverter implements RawdataConverter {

    private static final String RAWDATA_ITEMNAME_ENTRY = "entry";
    private static final String FIELDNAME_DC_MANIFEST = "dcManifest";
    private static final String FIELDNAME_CSV_DATA = "data";

    private final EnhetsregRawdataConverterConfig converterConfig;
    private final ValueInterceptorChain valueInterceptorChain;

    private DcManifestSchemaAdapter dcManifestSchemaAdapter;
    private Schema targetAvroSchema;
    private Schema dataSchema;

    public EnhetsregRawdataConverter(EnhetsregRawdataConverterConfig converterConfig, ValueInterceptorChain valueInterceptorChain) {
        this.converterConfig = converterConfig;
        this.valueInterceptorChain = valueInterceptorChain;
        dataSchema = readAvroSchema("schema/enhetsregisteret.avsc");
    }

    @Override
    public void init(Collection<RawdataMessage> sampleRawdataMessages) {
        log.info("Determine target avro schema from {}", sampleRawdataMessages);
        RawdataMessage sample = sampleRawdataMessages.stream()
                .findFirst()
                .orElseThrow(() ->
                        new EnhetsregRawdataConverterException("Unable to determine target avro schema since no sample rawdata messages were supplied. Make sure to configure `converter-settings.rawdata-samples`")
                );

        RawdataMessageAdapter msg = new RawdataMessageAdapter(sample);
        dcManifestSchemaAdapter = DcManifestSchemaAdapter.of(sample);

        String targetNamespace = "dapla.rawdata." + msg.getTopic().orElse("enhet");

        targetAvroSchema = new AggregateSchemaBuilder(targetNamespace)
                .schema(FIELDNAME_DC_MANIFEST, dcManifestSchemaAdapter.getDcManifestSchema())
                .schema("EnhetsRegisteret", dataSchema)
                .build();
    }

    public DcManifestSchemaAdapter dcManifestSchemaAdapter() {
        if (dcManifestSchemaAdapter == null) {
            throw new IllegalStateException("dcManifestSchemaAdapter is null. Make sure RawdataConverter#init() was invoked in advance.");
        }

        return dcManifestSchemaAdapter;
    }

    @Override
    public Schema targetAvroSchema() {
        if (targetAvroSchema == null) {
            throw new IllegalStateException("targetAvroSchema is null. Make sure RawdataConverter#init() was invoked in advance.");
        }

        return targetAvroSchema;
    }

    @Override
    public boolean isConvertible(RawdataMessage rawdataMessage) {
        return true;
    }

    @Override
    public ConversionResult convert(RawdataMessage rawdataMessage) {
        ConversionResultBuilder resultBuilder = ConversionResult.builder(new GenericRecordBuilder(targetAvroSchema));

        RawdataMessageAdapter.print(rawdataMessage); // TODO: Remove this ;-)
        addDcManifest(rawdataMessage, resultBuilder);
        // TODO: add converted data
        convert(rawdataMessage, resultBuilder);

        return resultBuilder.build();
    }

    private void convert(RawdataMessage rawdataMessage, ConversionResultBuilder builder) {

        RawdataMessageAdapter msg = new RawdataMessageAdapter(rawdataMessage);

        msg.getAllItemMetadata().values()
                .forEach(value -> {
                        String s = new String(rawdataMessage.get(value.getContentKey()));
                        builder.withRecord(value.getContentKey(), ToGenericRecord.from(s, dataSchema));


                });


    }

    void addDcManifest(RawdataMessage rawdataMessage, ConversionResultBuilder resultBuilder) {
        resultBuilder.withRecord(FIELDNAME_DC_MANIFEST, dcManifestSchemaAdapter().newRecord(rawdataMessage));
    }

    public static class EnhetsregRawdataConverterException extends RawdataConverterException {
        public EnhetsregRawdataConverterException(String msg) {
            super(msg);
        }

        public EnhetsregRawdataConverterException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}