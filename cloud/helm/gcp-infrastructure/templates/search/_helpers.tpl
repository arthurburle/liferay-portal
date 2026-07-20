{{- /* Labels for the search stack. */ -}}
{{- define "search.labels" -}}
component: search
{{ include "commonLabels" . }}
workload_class: standard
{{- end -}}

{{- define "search.name" -}}
{{- .Values.search.elasticsearch.name | default (printf "%s-es" .Release.Name) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "search.namespace" -}}
{{- printf "%s-elasticsearch" .Release.Namespace -}}
{{- end -}}

{{- define "search.serviceAccountName" -}}
search
{{- end -}}

{{- define "search.snapshotBucketName" -}}
{{- $envtag := .Values.environmentId -}}
{{- $projectId := .Values.projectId -}}
{{- $cloudProjectId := .Values.cloudProjectId -}}
{{- $bucketHash := printf "%s-%s-%s-search-snapshots" $cloudProjectId $projectId $envtag | sha256sum | trunc 6 -}}
{{- printf "search-snapshots-%s-%s-%s" $envtag $projectId $bucketHash | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "search.snapshotGsaEmail" -}}
{{- printf "%s@%s.iam.gserviceaccount.com" (include "search.snapshotGsaName" .) .Values.cloudProjectId -}}
{{- end -}}

{{- define "search.snapshotGsaName" -}}
{{- printf "search-%s" .Values.environmentId -}}
{{- end -}}