package datahub.protobuf.visitors.field;

import com.linkedin.common.GlobalTags;
import com.linkedin.common.GlossaryTermAssociation;
import com.linkedin.common.GlossaryTermAssociationArray;
import com.linkedin.common.GlossaryTerms;
import com.linkedin.common.TagAssociation;
import com.linkedin.common.TagAssociationArray;
import com.linkedin.common.urn.TagUrn;
import com.linkedin.schema.SchemaField;
import com.linkedin.tag.TagProperties;
import com.linkedin.util.Pair;
import datahub.protobuf.model.ProtobufField;
import datahub.protobuf.visitors.ProtobufExtensionUtil;
import datahub.protobuf.visitors.VisitContext;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProtobufExtensionFieldVisitor extends SchemaFieldVisitor {

    @Override
    public Stream<Pair<SchemaField, Double>> visitField(ProtobufField field, VisitContext context) {
        boolean isPrimaryKey = field.getFieldProto().getOptions().getAllFields().keySet().stream()
                .anyMatch(fieldDesc -> fieldDesc.getName().matches("(?i).*primary_?key"));

        List<TagAssociation> tags = Stream.concat(
                ProtobufExtensionUtil.extractTagPropertiesFromOptions(
                        field.getFieldProto().getOptions().getAllFields(),
                        context.getGraph().getRegistry()),
                        promotedTags(field, context))
                .distinct().map(tag -> new TagAssociation().setTag(new TagUrn(tag.getName())))
                .sorted(Comparator.comparing(t -> t.getTag().getName()))
                .collect(Collectors.toList());

        List<GlossaryTermAssociation> terms =  Stream.concat(
                ProtobufExtensionUtil.extractTermAssociationsFromOptions(
                                field.getFieldProto().getOptions().getAllFields(), context.getGraph().getRegistry()),
                promotedTerms(field, context))
                .distinct()
                .sorted(Comparator.comparing(a -> a.getUrn().getNameEntity()))
                .collect(Collectors.toList());

        return context.getFirstFieldPath(field).map(path -> Pair.of(
                new SchemaField()
                        .setFieldPath(context.getFieldPath(path))
                        .setNullable(!isPrimaryKey)
                        .setIsPartOfKey(isPrimaryKey)
                        .setDescription(field.comment())
                        .setNativeDataType(field.nativeType())
                        .setType(field.schemaFieldDataType())
                        .setGlobalTags(new GlobalTags().setTags(new TagAssociationArray(tags)))
                        .setGlossaryTerms(new GlossaryTerms()
                                .setTerms(new GlossaryTermAssociationArray(terms))
                                .setAuditStamp(context.getAuditStamp())),
                context.calculateSortOrder(path, field))).stream();
    }

    /**
     * Promote tags from nested message to field.
     * @return tags
     */
    private Stream<TagProperties> promotedTags(ProtobufField field, VisitContext context) {
        if (field.isMessage()) {
            return context.getGraph().outgoingEdgesOf(field).stream().flatMap(e ->
                    ProtobufExtensionUtil.extractTagPropertiesFromOptions(e.getEdgeTarget().messageProto()
                            .getOptions().getAllFields(), context.getGraph().getRegistry())
            ).distinct();
        } else {
            return Stream.of();
        }
    }

    /**
     * Promote terms from nested message to field.
     * @return terms
     */
    private Stream<GlossaryTermAssociation> promotedTerms(ProtobufField field, VisitContext context) {
        if (field.isMessage()) {
            return context.getGraph().outgoingEdgesOf(field).stream().flatMap(e ->
                    ProtobufExtensionUtil.extractTermAssociationsFromOptions(e.getEdgeTarget().messageProto()
                            .getOptions().getAllFields(), context.getGraph().getRegistry())
            ).distinct();
        } else {
            return Stream.of();
        }
    }

}
