{{- /*
Base mount path for chart-provided init scripts (helpers.sh, overlay-sync.sh,
wait-for-liferay.sh). Consumed by every init container that mounts a subPath
from the client-extension-init-scripts ConfigMap.
*/ -}}
{{- define "initScriptsPath" -}}
/liferay-init-scripts
{{- end }}

{{- /* Labels applied to every resource created by this chart. */ -}}
{{- define "commonLabels" -}}
environmentId: {{ .Values.environmentId }}
projectId: {{ .Values.projectId }}
workload_class: custom
{{- end -}}

{{- /*
Labels for a per-extension resource. Context: dict with "root" and "ext".
*/ -}}
{{- define "extLabels" -}}
app: {{ .ext.name }}
component: client-extension
{{ include "commonLabels" .root }}
{{- end -}}

{{- /* Immutable subset for pod selector. Context: dict "root" "ext". */ -}}
{{- define "extSelectorLabels" -}}
app: {{ .ext.name }}
component: client-extension
{{- end -}}

{{- define "serviceAccountName" -}}
{{- default .Release.Name .Values.serviceAccount.name -}}
{{- end -}}

{{- /* Labels for chart-wide (shared per-ns) resources. */ -}}
{{- define "sharedLabels" -}}
component: client-extension
{{ include "commonLabels" . }}
{{- end -}}