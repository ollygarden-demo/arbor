{{- define "arbor.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: arbor
app.kubernetes.io/managed-by: Helm
{{- end -}}

{{- define "arbor.image" -}}
{{ $.Values.imageRegistry }}/{{ .name }}:{{ $.Values.imageTag }}
{{- end -}}
