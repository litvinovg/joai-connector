<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
	xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
	xmlns:vitro="http://vitro.mannlib.cornell.edu/ns/vitro/0.7#"
	xmlns:vivo="http://vivoweb.org/ontology/core#"
	xmlns:owl="http://www.w3.org/2002/07/owl#"
	xmlns:skos="http://www.w3.org/2004/02/skos/core#"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:xsd="http://www.w3.org/2001/XMLSchema#"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fn="http://www.w3.org/2005/xpath-functions"
	xmlns:local="http://vivoweb.org/rdf-functions"
	xmlns:obo="http://purl.obolibrary.org/obo/"
	xmlns:vcard="http://www.w3.org/2006/vcard/ns#">

	<xsl:output method="xml" indent="yes" encoding="UTF-8" />

	<xsl:function name="local:id">
		<xsl:param name="cur_elements" as="node()*" />
		<xsl:for-each select="$cur_elements[self::*]">
			<xsl:choose>
				<xsl:when test="./@rdf:resource">
					<xsl:sequence select="./@rdf:resource" />
				</xsl:when>
				<xsl:otherwise>
					<xsl:sequence select="./*/@rdf:about" />
				</xsl:otherwise>
			</xsl:choose>
		</xsl:for-each>
	</xsl:function>

	<xsl:function name="local:get" as="node()*">
		<xsl:param name="cur_elements" as="node()*" />
		<xsl:for-each select="$cur_elements[self::*]">
			<xsl:choose>
				<xsl:when test="./@rdf:resource">
					<xsl:variable name="resource_uri"
						select="./@rdf:resource" />
					<xsl:sequence
						select="fn:root(current())//*[@rdf:about = $resource_uri]" />
				</xsl:when>
				<xsl:otherwise>
					<xsl:sequence select="./*" />
				</xsl:otherwise>
			</xsl:choose>
		</xsl:for-each>
	</xsl:function>

	<xsl:function name="local:distinct" as="node()*">
		<xsl:param name="cur_elements" as="node()*" />
		<xsl:for-each
			select="distinct-values($cur_elements[self::*]/@rdf:about)">
			<xsl:variable name="resource_uri" select="." />
			<xsl:sequence
				select="($cur_elements[@rdf:about = $resource_uri])[1]" />
		</xsl:for-each>
	</xsl:function>

	<xsl:template match="/">
		<xsl:for-each
			select="//*[rdfs:comment/text() = 'CERIF Person']">
			<xsl:call-template name="person" />
		</xsl:for-each>
	</xsl:template>


	<xsl:template name="person">
		<Person xmlns="https://www.openaire.eu/cerif-profile/1.2/">
			<xsl:attribute name="id">
                     <xsl:value-of
				select="concat('Persons/',@rdf:about)" />
                 </xsl:attribute>
			<xsl:call-template name="personName" />
			<xsl:call-template name="orcid" />
			<xsl:call-template name="email" />
			<xsl:call-template name="telephone" />
			<xsl:call-template name="affiliation" />
		</Person>
	</xsl:template>

	<xsl:template name="personName">
		<xsl:for-each
			select="local:get(local:get(obo:ARG_2000028)/vcard:hasName)[1]">
			<PersonName>
				<xsl:for-each select="vcard:familyName[1]">
					<FamilyNames>
						<xsl:value-of select="text()" />
					</FamilyNames>
				</xsl:for-each>
				<xsl:for-each select="vcard:givenName[1]">
					<FirstNames>
						<xsl:value-of select="text()" />
					</FirstNames>
				</xsl:for-each>
			</PersonName>
		</xsl:for-each>
	</xsl:template>

	<xsl:template name="orcid">
		<xsl:for-each select="local:id(vivo:orcidId)">
			<ORCID>
				<xsl:value-of select="." />
			</ORCID>
		</xsl:for-each>
	</xsl:template>

	<xsl:template name="affiliation">
		<xsl:for-each
			select="local:distinct(local:get(local:get(obo:RO_0000053)/vivo:roleContributesTo))">
			<Affiliation>
				<OrgUnit>
					<xsl:attribute name="id">
                    	<xsl:value-of
						select="concat('OrgUnits/',@rdf:about)" />
                 	</xsl:attribute>
					<xsl:for-each select="vivo:abbreviation">
						<Acronym>
							<xsl:value-of select="." />
						</Acronym>
					</xsl:for-each>
				</OrgUnit>
			</Affiliation>
		</xsl:for-each>

	</xsl:template>

	<xsl:template name="email">
		<xsl:for-each
			select="local:distinct(local:get(local:get(obo:ARG_2000028)/vcard:hasEmail))">
			<ElectronicAddress>
				<xsl:value-of
					select="concat('mailto:',vcard:email/text())" />
			</ElectronicAddress>
		</xsl:for-each>
	</xsl:template>

	<xsl:template name="telephone">
		<xsl:for-each
			select="local:distinct(local:get(local:get(obo:ARG_2000028)/vcard:hasTelephone))">
			<xsl:choose>
				<xsl:when
					test="local:id(vitro:mostSpecificType) = 'http://www.w3.org/2006/vcard/ns#Fax'">
					<ElectronicAddress>
						<xsl:value-of
							select="concat('fax:',vcard:telephone/text())" />
					</ElectronicAddress>
				</xsl:when>
				<xsl:otherwise>
					<ElectronicAddress>
						<xsl:value-of
							select="concat('tel:',vcard:telephone/text())" />
					</ElectronicAddress>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:for-each>
	</xsl:template>


</xsl:stylesheet>