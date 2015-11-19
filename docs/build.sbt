import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import sbtunidoc.Plugin.UnidocKeys._

common.ignore

site.settings

tutSettings

site.addMappingsToSiteDir(tut, "tut")

site.jekyllSupport()

// unidocSettings
// site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api")

ghpages.settings

ghpagesNoJekyll := false

includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml" | "*.md"

git.remoteRepo := "git@github.com:oncue/funnel.git"
